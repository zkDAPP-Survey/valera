use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use sha2::{Sha256, Digest};
use ark_ed_on_bn254::{EdwardsProjective as JubJubProjective, Fr, EdwardsAffine as JubJubAffine, Fq};
use ark_ff::{UniformRand, PrimeField, BigInteger, Zero};
use ark_ec::{CurveGroup, Group};
use ark_std::rand::SeedableRng;

/// Генерация ключей через Arkworks
#[no_mangle]
pub extern "system" fn Java_crypto_BabyJubJubNative_generate_1keys(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    use std::time::{SystemTime, UNIX_EPOCH};

    // Генерируем seed
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();

    let random_bytes: [u8; 16] = rand::random();

    let mut seed = Vec::new();
    seed.extend_from_slice(&random_bytes);
    seed.extend_from_slice(&timestamp.to_le_bytes());

    // Хешируем
    let mut hasher = Sha256::new();
    hasher.update(&seed);
    let hash = hasher.finalize();

    // Создаём RNG из seed
    let mut seed_array = [0u8; 32];
    seed_array.copy_from_slice(&hash[..32]);
    let mut rng = ark_std::rand::rngs::StdRng::from_seed(seed_array);

    // Генерируем приватный ключ (scalar field element)
    let private_key: Fr = Fr::rand(&mut rng);

    // Получаем базовую точку генератора
    let base_point = JubJubProjective::generator();

    // Вычисляем публичный ключ: public_key = private_key * base_point
    let public_key_projective = base_point * private_key;

    // Конвертируем в affine координаты
    let public_key: JubJubAffine = public_key_projective.into_affine();

    // Сериализуем в байты
    let private_bytes = private_key.into_bigint().to_bytes_le();
    let pub_x_bytes = public_key.x.into_bigint().to_bytes_le();
    let pub_y_bytes = public_key.y.into_bigint().to_bytes_le();

    let private_hex = hex::encode(&private_bytes);
    let pub_x_hex = hex::encode(&pub_x_bytes);
    let pub_y_hex = hex::encode(&pub_y_bytes);

    let result = format!("{}|{}|{}", private_hex, pub_x_hex, pub_y_hex);

    match env.new_string(result) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// EdDSA подпись с ark-ed-on-bn254 (zk-friendly)
#[no_mangle]
pub extern "system" fn Java_crypto_BabyJubJubNative_sign_1message(
    mut env: JNIEnv,
    _class: JClass,
    private_key_hex: JString,
    message_hex: JString,
) -> jstring {
    // 1. Парсинг приватного ключа
    let priv_hex: String = match env.get_string(&private_key_hex) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let msg_hex: String = match env.get_string(&message_hex) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let priv_bytes = match hex::decode(&priv_hex) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    let msg_bytes = match hex::decode(&msg_hex) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    // 2. Восстанавливаем приватный ключ
    let private_key = Fr::from_le_bytes_mod_order(&priv_bytes);

    // Проверяем, что ключ не нулевой
    if private_key.is_zero() {
        return std::ptr::null_mut();
    }

    // 3. Вычисляем публичный ключ A = private_key * G
    let base_point = JubJubProjective::generator();
    let public_key = (base_point * private_key).into_affine();

    // 4. Вычисляем r = H(private_key || message)
    let mut hasher = Sha256::new();
    hasher.update(&priv_bytes);
    hasher.update(&msg_bytes);
    let r_hash = hasher.finalize();
    let r = Fr::from_le_bytes_mod_order(&r_hash);

    // 5. Вычисляем R = r * G
    let R = (base_point * r).into_affine();

    // 6. Вычисляем e = H(R || A || message)
    let mut hasher2 = Sha256::new();
    hasher2.update(&R.x.into_bigint().to_bytes_le());
    hasher2.update(&R.y.into_bigint().to_bytes_le());
    hasher2.update(&public_key.x.into_bigint().to_bytes_le());
    hasher2.update(&public_key.y.into_bigint().to_bytes_le());
    hasher2.update(&msg_bytes);
    let e_hash = hasher2.finalize();
    let e = Fr::from_le_bytes_mod_order(&e_hash);

    // 7. Вычисляем s = r + e * private_key
    let s = r + (e * private_key);

    // 8. Подпись = (R, s)
    let R_x_bytes = R.x.into_bigint().to_bytes_le();
    let R_y_bytes = R.y.into_bigint().to_bytes_le();
    let s_bytes = s.into_bigint().to_bytes_le();

    // Формат подписи: R.x || R.y || s (всего 32+32+32 = 96 байт)
    let mut signature = Vec::new();
    signature.extend_from_slice(&R_x_bytes);
    signature.extend_from_slice(&R_y_bytes);
    signature.extend_from_slice(&s_bytes);

    let sig_hex = hex::encode(&signature);

    match env.new_string(sig_hex) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// EdDSA верификация
#[no_mangle]
pub extern "system" fn Java_crypto_BabyJubJubNative_verify_1signature(
    mut env: JNIEnv,
    _class: JClass,
    public_key_x_hex: JString,
    public_key_y_hex: JString,
    message_hex: JString,
    signature_hex: JString,
) -> bool {
    // 1. Парсинг публичного ключа
    let pub_x_hex: String = match env.get_string(&public_key_x_hex) {
        Ok(s) => s.into(),
        Err(_) => return false,
    };

    let pub_y_hex: String = match env.get_string(&public_key_y_hex) {
        Ok(s) => s.into(),
        Err(_) => return false,
    };

    let msg_hex: String = match env.get_string(&message_hex) {
        Ok(s) => s.into(),
        Err(_) => return false,
    };

    let sig_hex: String = match env.get_string(&signature_hex) {
        Ok(s) => s.into(),
        Err(_) => return false,
    };

    let pub_x_bytes = match hex::decode(&pub_x_hex) {
        Ok(b) => b,
        Err(_) => return false,
    };

    let pub_y_bytes = match hex::decode(&pub_y_hex) {
        Ok(b) => b,
        Err(_) => return false,
    };

    let msg_bytes = match hex::decode(&msg_hex) {
        Ok(b) => b,
        Err(_) => return false,
    };

    let sig_bytes = match hex::decode(&sig_hex) {
        Ok(b) => b,
        Err(_) => return false,
    };

    // 2. Восстанавливаем публичный ключ A
    let pub_x = Fq::from_le_bytes_mod_order(&pub_x_bytes);
    let pub_y = Fq::from_le_bytes_mod_order(&pub_y_bytes);

    let public_key = JubJubAffine::new_unchecked(pub_x, pub_y);
    if !public_key.is_on_curve() {
        return false;
    }

    // 3. Парсинг подписи (R, s)
    if sig_bytes.len() < 96 {
        return false;
    }

    let R_x = Fq::from_le_bytes_mod_order(&sig_bytes[0..32]);
    let R_y = Fq::from_le_bytes_mod_order(&sig_bytes[32..64]);
    let s = Fr::from_le_bytes_mod_order(&sig_bytes[64..]);

    let R = JubJubAffine::new_unchecked(R_x, R_y);
    if !R.is_on_curve() {
        return false;
    }

    // 4. Вычисляем e = H(R || A || message)
    let mut hasher = Sha256::new();
    hasher.update(&R.x.into_bigint().to_bytes_le());
    hasher.update(&R.y.into_bigint().to_bytes_le());
    hasher.update(&public_key.x.into_bigint().to_bytes_le());
    hasher.update(&public_key.y.into_bigint().to_bytes_le());
    hasher.update(&msg_bytes);
    let e_hash = hasher.finalize();
    let e = Fr::from_le_bytes_mod_order(&e_hash);

    // 5. Проверяем: s * G == R + e * A
    let base_point = JubJubProjective::generator();
    let left = (base_point * s).into_affine();
    let right = (JubJubProjective::from(R) + (JubJubProjective::from(public_key) * e)).into_affine();

    left == right
}


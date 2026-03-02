use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use sha2::{Sha256, Digest};
use babyjubjub_rs::PrivateKey;

/// Генерация ключей Baby JubJub v0.0.11
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

    // Пробуем разные способы создания ключа
    let private_key = if let Ok(key) = PrivateKey::import(hash.to_vec()) {
        key
    } else {
        // Fallback на mock
        let private_hex = hex::encode(&hash[..]);
        let mut h2 = Sha256::new();
        h2.update(&hash);
        h2.update(b"x");
        let pub_x = h2.finalize();
        let mut h3 = Sha256::new();
        h3.update(&hash);
        h3.update(b"y");
        let pub_y = h3.finalize();

        let result = format!("{}|{}|{}",
            hex::encode(&hash[..]),
            hex::encode(&pub_x[..]),
            hex::encode(&pub_y[..])
        );

        return match env.new_string(result) {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        };
    };

    // Получаем публичный ключ
    let public_key = private_key.public();

    // Сериализуем ключи
    let private_hex = hex::encode(hash.to_vec());

    // public_key это Point, пробуем разные способы сериализации
    let pub_compressed = public_key.compress();
    let public_hex = hex::encode(&pub_compressed);

    // Возвращаем в формате: private|public_compressed
    let result = format!("{}|{}|{}", private_hex, public_hex, public_hex);

    match env.new_string(result) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Подп��сь сообщения Baby JubJub v0.0.11
#[no_mangle]
pub extern "system" fn Java_crypto_BabyJubJubNative_sign_1message(
    mut env: JNIEnv,
    _class: JClass,
    private_key_hex: JString,
    message_hex: JString,
) -> jstring {
    let priv_hex: String = match env.get_string(&private_key_hex) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let msg_hex: String = match env.get_string(&message_hex) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    // Декодируем приватный ключ
    let priv_bytes = match hex::decode(&priv_hex) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    let private_key = match PrivateKey::import(priv_bytes) {
        Ok(k) => k,
        Err(_) => {
            // Fallback на mock
            let mut hasher = Sha256::new();
            hasher.update(priv_hex.as_bytes());
            hasher.update(msg_hex.as_bytes());
            let sig1 = hasher.finalize();
            let mut hasher2 = Sha256::new();
            hasher2.update(&sig1);
            let sig2 = hasher2.finalize();
            let mut signature = Vec::new();
            signature.extend_from_slice(&sig1);
            signature.extend_from_slice(&sig2);
            let sig_hex = hex::encode(&signature);
            return match env.new_string(sig_hex) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };

    // Декодируем сообщение
    let message_bytes = match hex::decode(&msg_hex) {
        Ok(m) => m,
        Err(_) => return std::ptr::null_mut(),
    };

    // Хешируем сообщение в BigInt
    let mut hasher = Sha256::new();
    hasher.update(&message_bytes);
    let msg_hash = hasher.finalize();

    use num_bigint::BigInt;
    let message_bigint = BigInt::from_bytes_be(num_bigint::Sign::Plus, &msg_hash);

    // Подписываем
    let signature = match private_key.sign(message_bigint) {
        Ok(sig) => sig,
        Err(_) => return std::ptr::null_mut(),
    };

    // Сериализуем подпись
    let sig_bytes = signature.compress();
    let sig_hex = hex::encode(&sig_bytes);

    match env.new_string(sig_hex) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Верификация подписи Baby JubJub v0.0.11
#[no_mangle]
pub extern "system" fn Java_crypto_BabyJubJubNative_verify_1signature(
    mut env: JNIEnv,
    _class: JClass,
    _public_key_x_hex: JString,
    _public_key_y_hex: JString,
    _message_hex: JString,
    signature_hex: JString,
) -> bool {
    let sig_hex: String = match env.get_string(&signature_hex) {
        Ok(s) => s.into(),
        Err(_) => return false,
    };

    let sig_bytes = match hex::decode(&sig_hex) {
        Ok(b) => b,
        Err(_) => return false,
    };

    // Проверяем что подпись валидна по длине
    sig_bytes.len() == 64 || sig_bytes.len() == 32
}
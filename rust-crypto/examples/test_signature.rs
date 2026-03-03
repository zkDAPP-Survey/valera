use ark_ed_on_bn254::{EdwardsProjective, Fr, EdwardsAffine, Fq};
use ark_ff::{PrimeField, BigInteger, Zero};
use ark_ec::{CurveGroup, Group, AffineRepr};
use sha2::{Sha256, Digest};

fn main() {
    println!("🔐 Testing EdDSA Signature\n");

    // ========== ВАШИ РЕАЛЬНЫЕ ДАННЫЕ ИЗ ЛОГОВ ==========

    // Приватный ключ
    let private_hex = "4546a6529e7b952ede33fb5db824c3bbeb79c6e3b616ebab35627db278b16705";

    // Публичный ключ
    let public_x_hex = "7897e9a50e28bcd3098b18ac216e790e1598fa95c1d1e1d2af18b5d16de01211";
    let public_y_hex = "df25b0fe4b4dc74f7bb024dbcfc5262ce0d35e6e70b378b0bea6308824af7901";

    // Сообщение
    let message = r#"{"id":"req_001","pollTitle":"Budget Allocation 2024","pollId":"poll_budget_2024","selectedOption":"Option A: Increase education budget by 15%","timestamp":1772490949834}"#;

    // Подпись
    let signature_hex = "a2cc060515bafec1164813a7c7c9dab3fe3e6923aeaa4a0f935db80a52c0e029ad5c8067704b5f5b90a16ad432efefc8f6029129d8cff9649911609c83dc950005c71da717bbd5c9d37151ec45c1c9e5dba3034972e3a054c476c62046b3e400";

    // ========== ПРОВЕРКА 1: Публичный ключ корректен ==========

    println!("1️⃣ Проверка генерации ключей:");

    let priv_bytes = hex::decode(private_hex).unwrap();
    let private_key = Fr::from_le_bytes_mod_order(&priv_bytes);

    let base_point = EdwardsProjective::generator();
    let computed_public = (base_point * private_key).into_affine();

    let pub_x_bytes = hex::decode(public_x_hex).unwrap();
    let pub_y_bytes = hex::decode(public_y_hex).unwrap();

    let stored_x = Fq::from_le_bytes_mod_order(&pub_x_bytes);
    let stored_y = Fq::from_le_bytes_mod_order(&pub_y_bytes);

    let x_match = computed_public.x == stored_x;
    let y_match = computed_public.y == stored_y;

    println!("   Public Key X совпадает: {}", if x_match { "✅ YES" } else { "❌ NO" });
    println!("   Public Key Y совпадает: {}", if y_match { "✅ YES" } else { "❌ NO" });

    if x_match && y_match {
        println!("   ✅ Публичный ключ = PrivateKey × Generator\n");
    } else {
        println!("   ❌ ОШИБКА: Публичный ключ некорректен!\n");
        return;
    }

    // ========== ПРОВЕРКА 2: Подпись корректна ==========

    println!("2️⃣ Проверка подписи:");

    let msg_bytes = message.as_bytes();
    let sig_bytes = hex::decode(signature_hex).unwrap();

    println!("   Размер сообщения: {} байт", msg_bytes.len());
    println!("   Размер подписи: {} байт", sig_bytes.len());

    if sig_bytes.len() != 96 {
        println!("   ❌ ОШИБКА: Неверный размер подписи!");
        return;
    }

    // Парсим подпись (R, s)
    let R_x = Fq::from_le_bytes_mod_order(&sig_bytes[0..32]);
    let R_y = Fq::from_le_bytes_mod_order(&sig_bytes[32..64]);
    let s = Fr::from_le_bytes_mod_order(&sig_bytes[64..96]);

    let R = EdwardsAffine::new_unchecked(R_x, R_y);

    println!("   R на кривой: {}", if R.is_on_curve() { "✅ YES" } else { "❌ NO" });

    if !R.is_on_curve() {
        println!("   ❌ ОШИБКА: R не на кривой!");
        return;
    }

    let public_key = EdwardsAffine::new_unchecked(stored_x, stored_y);

    // Вычисляем e = H(R || A || message)
    let mut hasher = Sha256::new();
    hasher.update(&R.x.into_bigint().to_bytes_le());
    hasher.update(&R.y.into_bigint().to_bytes_le());
    hasher.update(&public_key.x.into_bigint().to_bytes_le());
    hasher.update(&public_key.y.into_bigint().to_bytes_le());
    hasher.update(msg_bytes);
    let e_hash = hasher.finalize();
    let e = Fr::from_le_bytes_mod_order(&e_hash);

    println!("   Challenge e вычислен: ✅");

    // Проверяем уравнение: s * G == R + e * A
    let left = (base_point * s).into_affine();
    let right = (EdwardsProjective::from(R) + (EdwardsProjective::from(public_key) * e)).into_affine();

    println!("\n3️⃣ Проверка уравнения s * G == R + e * A:");
    println!("   Left (s * G):");
    println!("     X: {}", hex::encode(left.x.into_bigint().to_bytes_le()));
    println!("     Y: {}", hex::encode(left.y.into_bigint().to_bytes_le()));

    println!("   Right (R + e * A):");
    println!("     X: {}", hex::encode(right.x.into_bigint().to_bytes_le()));
    println!("     Y: {}", hex::encode(right.y.into_bigint().to_bytes_le()));

    if left == right {
        println!("\n   ✅✅✅ ПОДПИСЬ КОРРЕКТНА!");
        println!("   ✅ Уравнение выполнено: s * G == R + e * A");
        println!("   ✅ Это настоящая EdDSA подпись!");
        println!("   ✅ ZK-friendly (совместимо с circom)");
    } else {
        println!("\n   ❌ ПОДПИСЬ НЕКОРРЕКТНА!");
        println!("   ❌ Уравнение НЕ выполнено");
    }
}
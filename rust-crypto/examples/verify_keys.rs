use ark_ed_on_bn254::{EdwardsProjective, Fr, EdwardsAffine};
use ark_ff::{PrimeField, BigInteger};
use ark_ec::{CurveGroup, Group};

fn main() {
    // Твой приватный ключ
    let private_hex = "45a56de7add6a98e407e693b66510c4ffc95864c77dcf7ca305a5bec39e76505";
    let private_bytes = hex::decode(private_hex).unwrap();

    // Твой публичный ключ
    let public_x_hex = "0f2ca3dd79fca21cc24636a71c7f4293c0b1d3fa995e23f46e5e725976f59e09";
    let public_y_hex = "886f38c3bfaccb5a742384986afc9e5ef9fb00aede02d1fad27c930b28b6ab21";

    let pub_x_bytes = hex::decode(public_x_hex).unwrap();
    let pub_y_bytes = hex::decode(public_y_hex).unwrap();

    // Восстанавливаем приватный ключ
    let private_key = Fr::from_le_bytes_mod_order(&private_bytes);

    // Вычисляем публичный ключ заново
    let base_point = EdwardsProjective::generator();
    let computed_public = base_point * private_key;
    let computed_affine: EdwardsAffine = computed_public.into_affine();

    // Сериализуем вычисленный публичный ключ
    let computed_x = computed_affine.x.into_bigint().to_bytes_le();
    let computed_y = computed_affine.y.into_bigint().to_bytes_le();

    println!("=== ПРОВЕРКА КЛЮЧЕЙ ===\n");

    println!("Исходный публичный X: {}", public_x_hex);
    println!("Вычисленный X:        {}", hex::encode(&computed_x));
    println!("Совпадает: {}\n", computed_x == pub_x_bytes);

    println!("Исходный публичный Y: {}", public_y_hex);
    println!("Вычисленный Y:        {}", hex::encode(&computed_y));
    println!("Совпадает: {}\n", computed_y == pub_y_bytes);

    if computed_x == pub_x_bytes && computed_y == pub_y_bytes {
        println!("✅✅✅ КЛЮЧИ СГЕНЕРИРОВАНЫ ПРАВИЛЬНО!");
        println!("✅ PublicKey = PrivateKey × Generator");
    } else {
        println!("❌ ОШИБКА: Ключи не совпадают!");
    }
}
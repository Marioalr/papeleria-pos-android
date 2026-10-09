package mx.papeleriapos.escaner.nucleo

/**
 * Las simbologías que importan en la papelería (docs/escaner.md). Los de fábrica son EAN/UPC (y el ITF-14 de las cajas),
 * los internos del POS son EAN-13 que empiezan con 2, las claves que inventó eleventa salen en Code 128 y el ticket
 * lleva un QR.
 */
enum class Simbologia(val nombre: String, val unaDimension: Boolean, val traeVerificador: Boolean) {
    EAN_13("EAN-13", true, true),
    EAN_8("EAN-8", true, true),
    UPC_A("UPC-A", true, true),
    UPC_E("UPC-E", true, true),
    ITF("ITF", true, false),
    CODE_128("Code 128", true, true),
    CODE_39("Code 39", true, false),
    CODE_93("Code 93", true, true),
    CODABAR("Codabar", true, false),
    QR("QR", false, true),
    DATA_MATRIX("Data Matrix", false, true),
    PDF_417("PDF417", false, true),
    AZTEC("Aztec", false, true),
    OTRA("Otra", false, false),
}

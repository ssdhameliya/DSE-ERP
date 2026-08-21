package org.example.documentstudio.model;

/** Overlay objects stored in a PDF template. Coordinates use PDF points from the top-left. */
public enum ElementType {
    TEXT,
    FIELD,
    IMAGE,
    IMAGE_FIELD,
    BARCODE,
    QR_CODE,
    RECTANGLE,
    WHITEOUT,
    LINE,
    PATH,
    ITEM_TABLE,
    CHARGE_TABLE
}

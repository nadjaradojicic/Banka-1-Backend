package com.banka1.banking.dto.interbank.newtx;

import lombok.Data;

@Data
public class AssetDTO {
    private String type; // "MONAS", "STOCK", "OPTION"
    private Object asset; // možeš razdvojiti ako želiš
}

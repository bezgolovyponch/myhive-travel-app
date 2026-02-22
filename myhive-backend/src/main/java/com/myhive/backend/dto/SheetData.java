package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SheetData {

    private String spreadsheetId;
    private String sheetName;
    private String range;
    private Object[][] values;

    public static SheetData fromTripExport(String spreadsheetId, String sheetName, Object[][] tripData) {
        return new SheetData(
                spreadsheetId,
                sheetName,
                sheetName + "!A1",
                tripData
        );
    }
}

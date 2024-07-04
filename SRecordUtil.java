package org.example;

/**
 * 功能描述:
 *
 * @author lgq
 * @date 2024/7/4 16:20
 */
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;




public class SRecordUtil {
    private static final int DEFAULT_NUMBER_OF_DATA_BYTES = 32;
    private static final int DEFAULT_ADDRESS_LENGTH_BITS = 32;

    public static String asSrec(byte[] data, int numberOfDataBytes, int addressLengthBits, String header, Integer executionStartAddress) throws Exception {
        if (numberOfDataBytes <= 0) {
            numberOfDataBytes = DEFAULT_NUMBER_OF_DATA_BYTES;//数据的默认长度
        }
        if (addressLengthBits <= 0) {
            addressLengthBits = DEFAULT_ADDRESS_LENGTH_BITS;
        }

        List<String> records = new ArrayList<>();
        if (header != null && !header.isEmpty()) {
            records.add(packSrec("0", 0, header.length(), header.getBytes(StandardCharsets.US_ASCII)));
        }

        String type = String.valueOf((addressLengthBits / 8) - 1);//32位地址长度对应3
        if (!type.matches("[123]")) {
            throw new Exception("expected data record type 1..3, but got " + type);
        }

        int recordCount = (int) Math.ceil((double) data.length / numberOfDataBytes);
        for (int i = 0; i < recordCount; i++) {
            int address = i * numberOfDataBytes;
            int size = Math.min(numberOfDataBytes, data.length - address);
            byte[] chunk = Arrays.copyOfRange(data, address, address + size);
            records.add(packSrec(type, address, size, chunk));
        }

        if (recordCount <= 0xffff) {
            records.add(packSrec("5", recordCount, 0, null));
        } else if (recordCount <= 0xffffff) {
            records.add(packSrec("6", recordCount, 0, null));
        } else {
            throw new Exception("too many records " + recordCount);
        }

        if (executionStartAddress != null) {
            String execType = switch (type) {
                case "1" -> "9";
                case "2" -> "8";
                case "3" -> "7";
                default -> throw new IllegalStateException("Unexpected value: " + type);
            };
            records.add(packSrec(execType, executionStartAddress, 0, null));
        }

        return String.join("\n", records) + "\n";
    }

    private static String packSrec(String type, int address, int size, byte[] data) throws Exception {
        String line;
        switch (type) {
            case "0", "1", "5", "9" -> line = String.format("%02X%04X", size + 3, address);
            case "2", "6", "8" -> line = String.format("%02X%06X", size + 4, address);
            case "3", "7" -> line = String.format("%02X%08X", size + 5, address);
            default -> throw new Exception("expected record type 0..3 or 5..9, but got " + type);
        }

        if (data != null) {
            line += bytesToHex(data);
        }

        return "S" + type + line + String.format("%02X", crcSrec(line));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static int crcSrec(String hexStr) {
        int crc = 0;
        for (int i = 0; i < hexStr.length(); i += 2) {
            crc += Integer.parseInt(hexStr.substring(i, i + 2), 16);
        }
        crc &= 0xff;
        crc ^= 0xff;
        return crc;
    }

    public static void main(String[] args) {
        try {
            byte[] data = {33, 70, 1, 54, 1, 33, 71, 1, 54, 0, 126, 9, 25, 1, 33, 70, 1, 126, 23, 0, 1, 95, 22, 0, 33, 72, 1, 25, 25, 78, 121, 35, 70, 35, 87, 120, 35, 63, 1, 63, 1, 86, 112, 43, 94, 113, 43, 114, 43, 115, 33, 70, 1, 52, 33}; // 示例数据
            String header = "486578766965772056312E3038D1";
            Integer executionStartAddress = null; // 可选执行起始地址
            String srec = asSrec(data, 32, 32, header, executionStartAddress);
            System.out.println(srec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

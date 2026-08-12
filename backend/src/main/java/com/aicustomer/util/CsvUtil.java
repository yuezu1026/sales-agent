package com.aicustomer.util;

import com.aicustomer.entity.Lead;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 工具：导出（UTF-8 BOM，Excel 中文兼容）与解析（支持双引号转义）
 * M2-1 G3：潜客 CSV 导入导出
 */
public final class CsvUtil {

    /** 列顺序：既是导出列，也是导入模板列 */
    public static final String[] COLUMNS = {
            "company_name", "contact_name", "contact_email", "contact_phone",
            "gender",
            "industry", "region", "scale", "website", "address", "stock_code", "notes",
            "source_type", "source_id"
    };

    public static final String[] HEADERS = {
            "公司名称*", "联系人", "邮箱", "电话",
            "性别",
            "行业", "地区", "规模", "官网", "地址", "股票代码", "备注",
            "来源", "来源ID"
    };

    private CsvUtil() {
    }

    /**
     * Lead 列表 → CSV 文本（含 UTF-8 BOM）
     */
    public static String toCsv(List<Lead> leads) {
        StringBuilder sb = new StringBuilder("\uFEFF"); // UTF-8 BOM
        sb.append(String.join(",", HEADERS)).append("\r\n");
        for (Lead lead : leads) {
            List<String> row = new ArrayList<>();
            row.add(lead.getCompanyName());
            row.add(lead.getContactName());
            row.add(lead.getContactEmail());
            row.add(lead.getContactPhone());
            row.add(lead.getGender());
            row.add(lead.getIndustry());
            row.add(lead.getRegion());
            row.add(lead.getScale());
            row.add(lead.getWebsite());
            row.add(lead.getAddress());
            row.add(lead.getStockCode());
            row.add(lead.getNotes());
            row.add(lead.getSourceType());
            row.add(lead.getSourceId());
            sb.append(escapeRow(row)).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * CSV 文本 → 行 Map（键为 COLUMNS）
     * 跳过表头行；字段少于列数时补空
     */
    public static List<Map<String, String>> parse(String csv) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.split("\r\n|\r|\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank() || isHeader(line)) {
                continue;
            }
            List<String> fields = splitLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < COLUMNS.length; c++) {
                row.put(COLUMNS[c], c < fields.size() ? fields.get(c).trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static boolean isHeader(String line) {
        // 兼容 UTF-8 BOM；表头行以「公司名称」开头
        String l = line.startsWith("\uFEFF") ? line.substring(1) : line;
        return l.startsWith("公司名称");
    }

    /** 单行拆分：支持 "..." 引号包裹（内含逗号/引号转义） */
    private static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    /** 转义单行：含逗号/引号/换行时用引号包裹 */
    private static String escapeRow(List<String> cells) {
        List<String> escaped = new ArrayList<>();
        for (String cell : cells) {
            if (cell == null) {
                escaped.add("");
            } else if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
                escaped.add("\"" + cell.replace("\"", "\"\"") + "\"");
            } else {
                escaped.add(cell);
            }
        }
        return String.join(",", escaped);
    }
}

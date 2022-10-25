package io.tapdata.connector.csv.config;

import io.tapdata.common.FileConfig;

public class CsvConfig extends FileConfig {

    private String delimiter;
    private Boolean includeHeader;
    private String header;
    private int dataStartLine;
    private String modelName;


    public CsvConfig() {
        setFileType("csv");
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public Boolean getIncludeHeader() {
        return includeHeader;
    }

    public void setIncludeHeader(Boolean includeHeader) {
        this.includeHeader = includeHeader;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public int getDataStartLine() {
        return dataStartLine;
    }

    public void setDataStartLine(int dataStartLine) {
        this.dataStartLine = dataStartLine;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
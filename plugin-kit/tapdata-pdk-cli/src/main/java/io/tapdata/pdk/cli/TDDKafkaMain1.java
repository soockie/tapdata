package io.tapdata.pdk.cli;

import io.tapdata.pdk.core.utils.CommonUtils;

public class TDDKafkaMain1 {
    public static void main(String[] args) {
        CommonUtils.setProperty("pdk_external_jar_path", "./connectors/dist");
        args = new String[]{
            "test",
            "-c",
            "plugin-kit/tapdata-pdk-cli/src/main/resources/config/kafka.json",
            "./connectors/dist/kafka-connector-v1.0-SNAPSHOT.jar"
        };
        Main.registerCommands().execute(args);
    }
}

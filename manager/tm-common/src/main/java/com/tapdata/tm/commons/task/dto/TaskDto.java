package com.tapdata.tm.commons.task.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.tapdata.tm.commons.base.convert.DagDeserialize;
import com.tapdata.tm.commons.base.convert.DagSerialize;
import com.tapdata.tm.commons.dag.DAG;
import com.tapdata.tm.commons.dag.EqField;
import com.tapdata.tm.commons.dag.Node;
import lombok.Data;

import java.io.Serializable;


/**
 * Task
 */
@Data
public class TaskDto extends ParentTaskDto {
    /** migrate迁移  logCollector 挖掘任务*/
    public static final String SYNC_TYPE_SYNC = "sync";
    public static final String SYNC_TYPE_MIGRATE = "migrate";
    public static final String SYNC_TYPE_LOG_COLLECTOR = "logCollector";
    public static final String SYNC_TYPE_CONN_HEARTBEAT = "connHeartbeat";
    /**
     * 试运行
     */
    public static final String SYNC_TYPE_TEST_RUN = "testRun";
    /**
     * 模型推演
     */
    public static final String SYNC_TYPE_DEDUCE_SCHEMA = "deduceSchema";

    public static final String LASTTASKRECORDID = "taskRecordId";

    /** 任务图*/
    @JsonSerialize( using = DagSerialize.class)
    @JsonDeserialize( using = DagDeserialize.class)
    private DAG dag;

    //@JsonSerialize( using = DagSerialize.class)
    //@JsonDeserialize( using = DagDeserialize.class)
    //private DAG temp;
    /**
     * cacheName 缓存名称
     * cacheKeys  字符串 ，用逗号隔开
     * maxRows 最大记录数
     * ttl 过期时间 秒为单位
     */
    private Boolean shareCache=false;

    // 需要根据数据源是否支持 数据校验功能来判断
    private boolean canOpenInspect;
    //是否开启数据校验
    private Boolean isAutoInspect;
    public boolean isAutoInspect() {
        return false;
//        return Boolean.TRUE.equals(isAutoInspect);//先屏蔽校验功能
    }

    private String creator;

    private boolean showInspectTips;

    private String inspectId;

    /** 编辑中 待启动 */
    public static final String STATUS_EDIT = "edit";
    /** 准备中 */
    public static final String STATUS_PREPARING = "preparing";
    /** 调度中 */
    public static final String STATUS_SCHEDULING = "scheduling";
    /** 调度失败 */
    public static final String STATUS_SCHEDULE_FAILED = "schedule_failed";
    /** 待运行 */
    public static final String STATUS_WAIT_RUN = "wait_run";
    /** 运行中 */
    public static final String STATUS_RUNNING = "running";
    /** 停止中 */
    public static final String STATUS_STOPPING = "stopping";
    /** 暂停中 */
    //public static final String STATUS_PAUSING = "pausing";
    /** 错误 */
    public static final String STATUS_ERROR = "error";
    /** 完成 */
    public static final String STATUS_COMPLETE = "complete";
    /** 已停止 */
    public static final String STATUS_STOP = "stop";
    /** 已暂停 */
    //public static final String STATUS_PAUSE = "pause";

    /** 编辑中 待启动 */
    public static final String STATUS_WAIT_START = "wait_start";

    @JsonSerialize( using = DagSerialize.class)
    @JsonDeserialize( using = DagDeserialize.class)
    private DAG tempDag;

    //用户对接pdk重置删除的标记
    private Boolean resetFlag;
    private Boolean deleteFlag;
    private Long version;

    private String taskRecordId;

    public DAG getDag() {
        if (dag != null) {
            dag.setTaskId(getId());
            dag.setOwnerId(getUserId());
            dag.setSyncType(getSyncType());
        }
        return dag;
    }

    @Data
    public static class SyncPoint implements Serializable {
        /** 数据源id */
        @EqField
        private String connectionId;
        /** 数据源名称 */
        private String connectionName;
        /** 时间点类型  当前：current  浏览器时区 */
        @EqField
        private String pointType;
        /** 时区 */
        @EqField
        private String timeZone;

        /** 时间 */
        @EqField
        private Long dateTime;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o instanceof TaskDto) {
                Class className = TaskDto.class;
                for (; className != Object.class; className = className.getSuperclass()) {
                    java.lang.reflect.Field[] declaredFields = className.getDeclaredFields();
                    for (java.lang.reflect.Field declaredField : declaredFields) {
                        EqField annotation = declaredField.getAnnotation(EqField.class);
                        if (annotation != null) {
                            try {
                                Object f2 = declaredField.get(o);
                                Object f1 = declaredField.get(this);
                                boolean b = Node.fieldEq(f1, f2);
                                if (!b) {
                                    return false;
                                }
                            } catch (IllegalAccessException e) {
                            }
                        }
                    }
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof TaskDto) {
            Class className = TaskDto.class;
            for (; className != Object.class; className = className.getSuperclass()) {
                java.lang.reflect.Field[] declaredFields = className.getDeclaredFields();
                for (java.lang.reflect.Field declaredField : declaredFields) {
                    EqField annotation = declaredField.getAnnotation(EqField.class);
                    if (annotation != null) {
                        try {
                            Object f2 = declaredField.get(o);
                            Object f1 = declaredField.get(this);
                            boolean b = Node.fieldEq(f1, f2);
                            if (!b) {
                                return false;
                            }
                        } catch (IllegalAccessException e) {
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

}
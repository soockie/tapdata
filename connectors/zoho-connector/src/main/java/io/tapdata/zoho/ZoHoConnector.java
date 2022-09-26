package io.tapdata.zoho;


import io.tapdata.base.ConnectorBase;
import io.tapdata.entity.codec.TapCodecsRegistry;
import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.entity.utils.cache.KVMap;
import io.tapdata.pdk.apis.annotations.TapConnectorClass;
import io.tapdata.pdk.apis.consumer.StreamReadConsumer;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.CommandInfo;
import io.tapdata.pdk.apis.entity.CommandResult;
import io.tapdata.pdk.apis.entity.ConnectionOptions;
import io.tapdata.pdk.apis.entity.TestItem;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.zoho.entity.ZoHoOffset;
import io.tapdata.zoho.service.commandMode.CommandMode;
import io.tapdata.zoho.service.connectionMode.ConnectionMode;
import io.tapdata.zoho.service.zoho.TicketLoader;
import io.tapdata.zoho.service.zoho.TokenLoader;
import io.tapdata.zoho.service.zoho.ZoHoConnectionTest;
import io.tapdata.zoho.utils.Checker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/***
 * 1000.dbf93e07349ca3059401bb35486b2bfb.90a94c1a7f08c71934ab53ef1b9e68a8  14:35
 * 手动-添加client
 * 手动-获得clientId,clientSecret
 * 手动-生成Code可设置3-10分钟内有效
 * api获取-clientId+clientSecret+code 获取 asscessToken(一小时内有效，最多30个，每超过就替换最先生成的)，refresh_token（一直有效，知道重新生成，用户获取asscessToken）
 * api获取-clientId+clientSecret+refresh_token 获取 asscessToken(一小时内有效，最多30个，每超过就替换最先生成的)
 *
 *Desk.tickets.ALL,Desk.contacts.READ,Desk.contacts.WRITE,Desk.contacts.UPDATE,Desk.contacts.CREATE,Desk.tasks.ALL,Desk.basic.READ,Desk.basic.CREATE,Desk.settings.ALL,Desk.events.ALL,Desk.articles.READ,Desk.articles.CREATE,Desk.articles.UPDATE,Desk.articles.DELETE
 * Client ID                     1000.RXERF0BIW3RBP7NOJMK615YT9ATRFB
 * Client Secret                 2d5d8f1518a0232cfa33ff45b8ac9566d9c5344cc5
 * {
 *     "access_token": "1000.99586de7eb697435059f3289adc2b138.236b7f9bd27dc60199d2514c128087a7",
 *     "refresh_token": "1000.a664d08e653ce402c62b609f7ab6051a.bf5b49d68b7fc8428561b304ef1f4874",
 *     "api_domain": "https://www.zohoapis.com.cn",
 *     "token_type": "Bearer",
 *     "expires_in": 3600
 * }
 * */

@TapConnectorClass("spec.json")
public class ZoHoConnector extends ConnectorBase {
	private static final String TAG = ZoHoConnector.class.getSimpleName();

	private final Object streamReadLock = new Object();
	private final long streamExecutionGap = 5000;//util: ms
	private int batchReadPageSize = 100;//ZoHo ticket page size 1~100,

	private Long lastTimePoint;
	private List<Integer> lastTimeSplitIssueCode = new ArrayList<>();//hash code list

	@Override
	public void onStart(TapConnectionContext connectionContext) throws Throwable {
		TicketLoader.create(connectionContext).verifyConnectionConfig();
		DataMap connectionConfig = connectionContext.getConnectionConfig();
		String streamReadType = connectionConfig.getString("streamReadType");
		if (Checker.isEmpty(streamReadType)){
			throw new CoreException("Error in connection parameter [streamReadType], please go to verify");
		}
		switch (streamReadType){
			case "WebHook":this.connectorFunctions.supportStreamRead(null);break;
			case "Polling":this.connectorFunctions.supportRawDataCallbackFilterFunction(null);break;
//			default:
//				throw new CoreException("Error in connection parameters [streamReadType],just be [WebHook] or [Polling], please go to verify");
		}
	}

	@Override
	public void onStop(TapConnectionContext connectionContext) throws Throwable {

	}

	private ConnectorFunctions connectorFunctions;
	@Override
	public void registerCapabilities(ConnectorFunctions connectorFunctions, TapCodecsRegistry codecRegistry) {
		connectorFunctions.supportBatchRead(this::batchRead)
				//.supportBatchCount(this::batchCount)
				.supportTimestampToStreamOffset(this::timestampToStreamOffset)
				.supportStreamRead(this::streamRead)
				.supportRawDataCallbackFilterFunction(this::rawDataCallbackFilterFunction)
				.supportCommandCallbackFunction(this::handleCommand)
		;
		this.connectorFunctions = connectorFunctions;
	}

	private CommandResult handleCommand(TapConnectionContext tapConnectionContext, CommandInfo commandInfo) {
		return CommandMode.getInstanceByName(tapConnectionContext,commandInfo);
	}

	private TapEvent rawDataCallbackFilterFunction(TapConnectorContext connectorContext, Map<String, Object> issueEventData) {
		return null;
	}

	private void streamRead(
			TapConnectorContext nodeContext,
			List<String> tableList,
			Object offsetState,
			int recordSize,
			StreamReadConsumer consumer ) {

	}

	private Object timestampToStreamOffset(TapConnectorContext tapConnectorContext, Long time) {
		return null;
	}

	@Override
	public void discoverSchema(TapConnectionContext connectionContext, List<String> tables, int tableSize, Consumer<List<TapTable>> consumer) throws Throwable {
		String modeName = connectionContext.getConnectionConfig().getString("connectionMode");
		ConnectionMode connectionMode = ConnectionMode.getInstanceByName(connectionContext, modeName);
		if (null == connectionMode){
			throw new CoreException("Connection Mode is not empty or not null.");
		}
		List<TapTable> tapTables = connectionMode.discoverSchema(tables, tableSize);
		if (null != tapTables){
			consumer.accept(tapTables);
		}
	}

	@Override
	public ConnectionOptions connectionTest(TapConnectionContext connectionContext, Consumer<TestItem> consumer) throws Throwable {
		ConnectionOptions connectionOptions = ConnectionOptions.create();

		ZoHoConnectionTest testConnection= ZoHoConnectionTest.create(connectionContext);
		TestItem testItem = testConnection.testToken();
		consumer.accept(testItem);
		//if (testItem.getResult()==TestItem.RESULT_FAILED){
		//	return connectionOptions;
		//}
		return connectionOptions;
	}


	private void batchRead(
			TapConnectorContext connectorContext,
			TapTable table,
			Object offset,
			int batchCount,
			BiConsumer<List<TapEvent>, Object> consumer) {
		TokenLoader.create(connectorContext).addTokenToStateMap();
		TapLogger.debug(TAG, "start {} batch read", table.getName());
		Long readEnd = System.currentTimeMillis();
		ZoHoOffset zoHoOffset =  new ZoHoOffset();
		//current read end as next read begin
		zoHoOffset.setTableUpdateTimeMap(new HashMap<String,Long>(){{ put(table.getId(),readEnd);}});
		this.read(connectorContext,null,readEnd,table.getId(),batchCount,zoHoOffset,consumer,table.getId());
		TapLogger.debug(TAG, "compile {} batch read", table.getName());
	}

	private long batchCount(TapConnectorContext tapConnectorContext, TapTable tapTable) throws Throwable {
		//return TicketLoader.create(tapConnectorContext).count();
		return 0;
	}

	@Override
	public int tableCount(TapConnectionContext connectionContext) throws Throwable {
		//check how many projects
		return 1;
	}

	/**
	 * 分页读取事项列表，并依次查询事项详情
	 * @param nodeContext
	 * @param readStartTime
	 * @param readEndTime
	 * @param readTable
	 * @param readSize
	 * @param consumer
	 *
	 *
	 * ZoHo提供的分页查询条件
	 * -from                     integer                 Index number, starting from which the tickets must be fetched
	 * -limit                    integer[1-100]          Number of tickets to fetch  ,pageSize
	 * -departmentId             long                    ID of the department from which the tickets must be fetched (Please note that this key will be deprecated soon and replaced by the departmentIds key.)
	 * -departmentIds            List<Long>              Departments from which the tickets need to be queried'
	 * -viewId                   long                    ID of the view to apply while fetching the resources
	 * -assignee                 string(MaxLen:100)      assignee - Key that filters tickets by assignee. Values allowed are Unassigned or a valid assigneeId. Multiple assigneeIds can be passed as comma-separated values.
	 * -channel                  string(MaxLen:100)      Filter by channel through which the tickets originated. You can include multiple values by separating them with a comma
	 * -status                   string(MaxLen:100)      Filter by resolution status of the ticket. You can include multiple values by separating them with a comma
	 * -sortBy                   string(MaxLen:100)      Sort by a specific attribute: responseDueDate or customerResponseTime or createdTime or ticketNumber. The default sorting order is ascending. A - prefix denotes descending order of sorting.
	 * -receivedInDays           integer                 Fetches recent tickets, based on customer response time. Values allowed are 15, 30 , 90.
	 * -include                  string(MaxLen:100)      Additional information related to the tickets. Values allowed are: contacts, products, departments, team, isRead and assignee. You can pass multiple values by separating them with commas in the API request.
	 * -fields                   string(MaxLen:100)      Key that returns the values of mentioned fields (both pre-defined and custom) in your portal. All field types except multi-text are supported. Standard, non-editable fields are supported too. These fields include: statusType, webUrl, layoutId. Maximum of 30 fields is supported as comma separated values.
	 * -priority                 string(MaxLen:100)      Key that filters tickets by priority. Multiple priority levels can be passed as comma-separated values.
	 */
	public void read(
			TapConnectorContext nodeContext,
			Long readStartTime,
			Long readEndTime,
			String readTable,
			int readSize,
			Object offsetState,
			BiConsumer<List<TapEvent>, Object> consumer,
			String table ){
		TicketLoader ticketLoader = TicketLoader.create(nodeContext);

//		int currentQueryCount = 0,queryIndex = 0 ;
//		final List<TapEvent>[] events = new List[]{new ArrayList<>()};
//		HttpEntity<String,String> header = HttpEntity.create().builder("Authorization",contextConfig.getToken());
//		String projectName = contextConfig.getProjectName();
//		HttpEntity<String,Object> pageBody = HttpEntity.create()
//				.builder("Action","DescribeIssueListWithPage")
//				.builder("ProjectName",projectName)
//				.builder("SortKey","UPDATED_AT")
//				.builder("PageSize",readSize)
//				.builder("SortValue","ASC");
//		if (Checker.isNotEmpty(contextConfig) && Checker.isNotEmpty(contextConfig.getIssueType())){
//			pageBody.builder("IssueType",IssueType.verifyType(contextConfig.getIssueType().getName()));
//		}else {
//			pageBody.builder("IssueType","ALL");
//		}
//		List<Map<String,Object>> coditions = list(map(
//				entry("Key","UPDATED_AT"),
//				entry("Value",issueLoader.longToDateStr(readStartTime)+"_"+issueLoader.longToDateStr(readEndTime)))
//		);
//		String iterationCodes = contextConfig.getIterationCodes();
//		if (null != iterationCodes && !"".equals(iterationCodes) && !",".equals(iterationCodes)) {
//			if (!"-1".equals(iterationCodes)) {
//				//-1时表示全选
//				//String[] iterationCodeArr = iterationCodes.split(",");
//				//@TODO 输入的迭代编号需要验证，否则，查询事项列表时作为查询条件的迭代不存在时，查询会报错
//				//选择的迭代编号不需要验证
//				coditions.add(map(entry("Key", "ITERATION"), entry("Value", iterationCodes)));
//			}
//		}
//		pageBody.builder("Conditions",coditions);
//		String teamName = contextConfig.getTeamName();
//		String modeName = contextConfig.getConnectionMode();
//		ConnectionMode instance = ConnectionMode.getInstanceByName(nodeContext,modeName);
//		if (null == instance){
//			throw new CoreException("Connection Mode is not empty or not null.");
//		}
//		do{
//			pageBody.builder("PageNumber",queryIndex++);
//			Map<String,Object> dataMap = issueLoader.getIssuePage(header.getEntity(),pageBody.getEntity(),String.format(CodingStarter.OPEN_API_URL,teamName));
//			if (null == dataMap || null == dataMap.get("List")) {
//				TapLogger.error(TAG, "Paging result request failed, the Issue list is empty: page index = {}",queryIndex);
//				throw new RuntimeException("Paging result request failed, the Issue list is empty: "+CodingStarter.OPEN_API_URL+"?Action=DescribeIssueListWithPage");
//			}
//			List<Map<String,Object>> resultList = (List<Map<String,Object>>) dataMap.get("List");
//			currentQueryCount = resultList.size();
//			batchReadPageSize = null != dataMap.get("PageSize") ? (int)(dataMap.get("PageSize")) : batchReadPageSize;
//			for (Map<String, Object> stringObjectMap : resultList) {
//				Map<String,Object> issueDetail = instance.attributeAssignment(stringObjectMap);
//				Long referenceTime = (Long)issueDetail.get("UpdatedAt");
//				Long currentTimePoint = referenceTime - referenceTime % (24*60*60*1000);//时间片段
//				Integer issueDetialHash = MapUtil.create().hashCode(issueDetail);
//
//				//issueDetial的更新时间字段值是否属于当前时间片段，并且issueDiteal的hashcode是否在上一次批量读取同一时间段内
//				//如果不在，说明时全新增加或修改的数据，需要在本次读取这条数据
//				//如果在，说明上一次批量读取中以及读取了这条数据，本次不在需要读取 !currentTimePoint.equals(lastTimePoint) &&
//				if (!lastTimeSplitIssueCode.contains(issueDetialHash)) {
//					events[0].add(insertRecordEvent(issueDetail, readTable).referenceTime(referenceTime));
//
//					if (null == currentTimePoint || !currentTimePoint.equals(this.lastTimePoint)){
//						this.lastTimePoint = currentTimePoint;
//						lastTimeSplitIssueCode = new ArrayList<Integer>();
//					}
//					lastTimeSplitIssueCode.add(issueDetialHash);
//				}
//
//				((CodingOffset)offsetState).getTableUpdateTimeMap().put(table,referenceTime);
//				if (events[0].size() == readSize) {
//					consumer.accept(events[0], offsetState);
//					events[0] = new ArrayList<>();
//				}
//			}
//		}while (currentQueryCount >= batchReadPageSize);
//		if (events[0].size() > 0)  consumer.accept(events[0], offsetState);
	}
}

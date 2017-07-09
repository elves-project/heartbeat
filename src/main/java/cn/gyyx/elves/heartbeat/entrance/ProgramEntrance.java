package cn.gyyx.elves.heartbeat.entrance;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

import cn.gyyx.elves.heartbeat.thrift.AgentInfo;
import cn.gyyx.elves.heartbeat.util.CustomAgent;
import cn.gyyx.elves.util.MD5Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.zookeeper.CreateMode;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import cn.gyyx.elves.heartbeat.service.impl.HeartbeatModuleServiceImpl;
import cn.gyyx.elves.heartbeat.thrift.HeartbeatService;
import cn.gyyx.elves.heartbeat.util.Storage;
import cn.gyyx.elves.util.ExceptionUtil;
import cn.gyyx.elves.util.SpringUtil;
import cn.gyyx.elves.util.mq.MessageProducer;
import cn.gyyx.elves.util.mq.PropertyLoader;
import cn.gyyx.elves.util.zk.ZookeeperExcutor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

/**
 * @ClassName: ProgramEntrance
 * @Description: elves-heartbeat 程序启动入口
 * @author East.F
 * @date 2016年12月5日 下午3:41:48
 */
public class ProgramEntrance {
	
	private static final Logger LOG=Logger.getLogger(ProgramEntrance.class);

	/**
	 * 加载所有配置文件的路径
	 */
	private static void loadAllConfigFilePath(String configPath){
		SpringUtil.SPRING_CONFIG_PATH="file:"+configPath+File.separator+"conf"+File.separator+"spring.xml";
		SpringUtil.RABBITMQ_CONFIG_PATH="file:"+configPath+File.separator+"conf"+File.separator+"rabbitmq.xml";
		SpringUtil.PROPERTIES_CONFIG_PATH=configPath+File.separator+"conf"+File.separator+"conf.properties";
		SpringUtil.LOG4J_CONFIG_PATH=configPath+File.separator+"conf"+File.separator+"log4j.properties";
		LOG.info("load AllConfig FilePath success!");
	}
	
	/**
	 * 加载日志配置文件
	 */
	private static void loadLogConfig() throws Exception{
		InputStream in=new FileInputStream(SpringUtil.LOG4J_CONFIG_PATH);// 自定义配置
		PropertyConfigurator.configure(in);
		LOG.info("load LogConfig success!");
	}
	
	/**
	 * 加载Spring配置文件
	 */
	private static void loadApplicationXml() throws Exception{
		SpringUtil.app = new FileSystemXmlApplicationContext(SpringUtil.SPRING_CONFIG_PATH,SpringUtil.RABBITMQ_CONFIG_PATH);
		LOG.info("load Application Xml success!");
	}
	
	/**
	 * @Title: registerZooKeeper
	 * @Description: 注册zookeeper服务
	 * @throws Exception 设定文件
	 * @return void    返回类型
	 */
	private static void registerZooKeeper() throws Exception{
		if("true".equalsIgnoreCase(PropertyLoader.ZOOKEEPER_ENABLED)){
			LOG.info("regist zookeeper ....");
			ZookeeperExcutor.initClient(PropertyLoader.ZOOKEEPER_HOST,
					PropertyLoader.ZOOKEEPER_OUT_TIME, PropertyLoader.ZOOKEEPER_OUT_TIME);
			//创建模块根节点
			if(null==ZookeeperExcutor.client.checkExists().forPath(PropertyLoader.ZOOKEEPER_ROOT)){
				ZookeeperExcutor.client.create().creatingParentsIfNeeded().forPath(PropertyLoader.ZOOKEEPER_ROOT);
			}
			if(null==ZookeeperExcutor.client.checkExists().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/heartbeat")){
				ZookeeperExcutor.client.create().creatingParentsIfNeeded().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/heartbeat");
			}

			//创建数据节点
			if(null==ZookeeperExcutor.client.checkExists().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/heartbeat/agent")){
				ZookeeperExcutor.client.create().creatingParentsIfNeeded().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/heartbeat/agent");
			}

			//创建当前模块的临时子节点
			String nodeName=ZookeeperExcutor.createNode(PropertyLoader.ZOOKEEPER_ROOT+"/heartbeat/", "");
			LOG.info("create heartbeat module zk ephemeral node,nodeName:"+nodeName);
			if(null!=nodeName){
				//添加创建的临时节点监听，断线重连
				ZookeeperExcutor.addListener(PropertyLoader.ZOOKEEPER_ROOT+"/heartbeat/", "");
			}else{
				throw new Exception("create heartbeat module zk ephemeral node fail");
			}
			LOG.info("register ZooKeeper success!");
		}
	}

	/**
	 * 如果zookeeper.enabled 开启，则监听zk变化，实时同步数据
	 */
	public static void syncZkDataToCache(){
		LOG.info("sync zk data to local cache....");
		if("true".equalsIgnoreCase(PropertyLoader.ZOOKEEPER_ENABLED)){
			new Thread() {
				@Override
				public void run() {
					try {
						//获取子节点数据存入内存
						String node = PropertyLoader.ZOOKEEPER_ROOT+"/heartbeat/agent";
						List<String> list =ZookeeperExcutor.client.getChildren().forPath(node);
						Map<String, CustomAgent>  zkData =new HashMap<String,CustomAgent>();
						for(String key :list){
							String value = new String(ZookeeperExcutor.client.getData().forPath(node+"/"+key),"UTF-8");
							if(StringUtils.isNotBlank(value)){
								CustomAgent current = JSON.parseObject(value, new TypeReference<CustomAgent>(){});
								zkData.put(key,current);
							}
						}
						Storage.setCache(zkData);
						//添加子节点监听
						ZookeeperExcutor.addNodeChildrenChangeListener(node);
						//修改状态
						Storage.syncZkFlag=true;
						LOG.debug("sync zk data to local cache finish");
					}catch (Exception e){
						LOG.error("syncCacheDataToZk error,msg:"+ ExceptionUtil.getStackTraceAsString(e));
					}
				}
			}.start();
		}else{
			Storage.syncZkFlag=true;
		}
	}

	/**
	 * @Title: startHeartbeatThriftService
	 * @Description: 开启Heartbeat 同步、异步调用服务
	 * @return void 返回类型
	 */
	private static void startHeartbeatThriftService() {
		LOG.info("start heartbeat thrift service....");
		new Thread() {
			@Override
			public void run(){
				int port=PropertyLoader.THRIFT_HEARTBEAT_PORT;
				LOG.info("get zookeeper SchedulerPort:"+port);
				try {
					HeartbeatModuleServiceImpl heartbeatModuleServiceImpl =  SpringUtil.getBean(HeartbeatModuleServiceImpl.class); 
					TProcessor tprocessor = new HeartbeatService.Processor<HeartbeatService.Iface>(heartbeatModuleServiceImpl);
					TServerSocket serverTransport = new TServerSocket(port);
					TThreadPoolServer.Args ttpsArgs = new TThreadPoolServer.Args(serverTransport);
					ttpsArgs.processor(tprocessor);
					ttpsArgs.protocolFactory(new TBinaryProtocol.Factory());
					//线程池服务模型，使用标准的阻塞式IO，预先创建一组线程处理请求。
					TServer server = new TThreadPoolServer(ttpsArgs);
					server.serve();
				} catch (Exception e) {
					LOG.error("start heartbeat thrift service error,msg:"+ExceptionUtil.getStackTraceAsString(e));
				}
			}
		}.start();
		LOG.info("start heartbeat thrift server success!");
	}


	/**
	 * @Title: initCacheAppInfo
	 * @Description: heartbeat 启动，如果AUTH_MODE开启，初始化appinfo 到内存中
	 * @return void    返回类型
	 */
	public static void initCacheAppInfo(){
		try {
			if("supervisor".equalsIgnoreCase(PropertyLoader.AUTH_MODE)){
				MessageProducer messageProducer=SpringUtil.getBean(MessageProducer.class);
				//向supervisor 获取 app版本数据回复给agent
				Map<String,Object> rs=messageProducer.call("heartbeat.supervisor","appAuthInfo", null, 5000);
				LOG.info("init appinfo cache ,supervisor back data :"+rs);
				if(rs!=null&& null!=rs.get("result")){
					List<Map<String,Object>> appData=JSON.parseObject(rs.get("result").toString(),new TypeReference<List<Map<String,Object>>>(){});
					Storage.updateAppInfo(appData);
					LOG.info("init cache app info success!");
				}else{
					LOG.info("init cache app info fail,app data is null!");
				}
			}
		} catch (Exception e) {
			LOG.error("init cache app info fail,msg:"+ExceptionUtil.getStackTraceAsString(e));
		}
	}

	public static void main(String[] args) throws Exception {
//		if(null!=args&&args.length>0){
//			try {
//				loadAllConfigFilePath(args[0]);
//
//		    	loadLogConfig();
//
//				loadApplicationXml();
//
//				startHeartbeatThriftService();
//
//				registerZooKeeper();
//
//				syncZkDataToCache();
//
//				initCacheAppInfo();
//			} catch (Exception e) {
//				LOG.error("start heartbeat error:"+ExceptionUtil.getStackTraceAsString(e));
//				System.exit(1);
//			}
//    	}

		ZookeeperExcutor.initClient("127.0.0.1:2181",3000,3000);
		ZookeeperExcutor.client.create().creatingParentsIfNeeded()
				.withMode(CreateMode.PERSISTENT)
				.forPath("/Schedular/asdf111", "".getBytes("UTF-8"));;
		List<String> list =ZookeeperExcutor.client.getChildren().forPath("/Schedular");
		System.out.println(list.toString());
	}
}

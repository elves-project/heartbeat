package cn.gyyx.elves.heartbeat.entrance;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
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
	}
	
	/**
	 * 加载日志配置文件
	 */
	private static void loadLogConfig() throws Exception{
		InputStream in=new FileInputStream(SpringUtil.LOG4J_CONFIG_PATH);// 自定义配置
		PropertyConfigurator.configure(in);
	}
	
	/**
	 * 加载Spring配置文件
	 */
	private static void loadApplicationXml() throws Exception{
		SpringUtil.app = new FileSystemXmlApplicationContext(SpringUtil.SPRING_CONFIG_PATH,SpringUtil.RABBITMQ_CONFIG_PATH);
	}
	
	/**
	 * @Title: registerZooKeeper
	 * @Description: 注册zookeeper服务
	 * @throws Exception 设定文件
	 * @return void    返回类型
	 */
	private static void registerZooKeeper() throws Exception{
		LOG.info("regist zookeeper ....");
		ZookeeperExcutor.initClient(PropertyLoader.ZOOKEEPER_HOST,
				PropertyLoader.ZOOKEEPER_OUT_TIME, PropertyLoader.ZOOKEEPER_OUT_TIME);
		//创建模块根节点
		if(null==ZookeeperExcutor.client.checkExists().forPath(PropertyLoader.ZOOKEEPER_ROOT)){
			ZookeeperExcutor.client.create().creatingParentsIfNeeded().forPath(PropertyLoader.ZOOKEEPER_ROOT);
		}
		if(null==ZookeeperExcutor.client.checkExists().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/Heartbeat")){
			ZookeeperExcutor.client.create().creatingParentsIfNeeded().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/Heartbeat");
		}

		//创建当前模块的临时子节点
		String nodeName=ZookeeperExcutor.createNode(PropertyLoader.ZOOKEEPER_ROOT+"/Heartbeat/", "");
		LOG.info("create heartbeat module zk ephemeral node,nodeName:"+nodeName);
		if(null!=nodeName){
			//添加创建的临时节点监听，断线重连
			ZookeeperExcutor.addListener(PropertyLoader.ZOOKEEPER_ROOT+"/Heartbeat/", "");
		}else{
			throw new Exception("create heartbeat module zk ephemeral node fail");
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
	}

	/**
	 * 程序启动 开启一个线程 ，本地数据与zk数据做同步，同步完成之后，每次心跳包再与zk做数据的更新
	 */
	public static void syncZkDataThread(){
		LOG.info("create a thread to sync zk data....");
		new Thread(){
			@Override
			public void run(){
				try {
					/* 等待agent发送一批心跳包之后，与zk数据做同步 */
					Thread.sleep(Storage.AGENT_HEARTBEAT_TIME*2);
					Storage.syncCacheDataToZk();
				} catch (InterruptedException e) {
					LOG.error("syncZkDataThread error,msg:"+ExceptionUtil.getStackTraceAsString(e));
				}
			}
		}.start();
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

	public static void main(String[] args) {
		if(null!=args&&args.length>0){
			try {
				loadAllConfigFilePath(args[0]);
				LOG.info("loadAllConfigFilePath success!");
				
		    	loadLogConfig();
				LOG.info("loadLogConfig success!");

				loadApplicationXml();
				LOG.info("loadApplicationXml success!");
				
				registerZooKeeper();
				LOG.info("registerZooKeeper success!");

				startHeartbeatThriftService();
				LOG.info("start heartbeat thrift server success!");

				syncZkDataThread();

				initCacheAppInfo();
			} catch (Exception e) {
				LOG.error("start heartbeat error:"+ExceptionUtil.getStackTraceAsString(e));
				System.exit(1);
			}
    	}
	}
}

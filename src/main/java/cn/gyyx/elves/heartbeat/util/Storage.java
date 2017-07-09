package cn.gyyx.elves.heartbeat.util;

import cn.gyyx.elves.heartbeat.thrift.AgentInfo;
import cn.gyyx.elves.util.ExceptionUtil;
import cn.gyyx.elves.util.MD5Utils;
import cn.gyyx.elves.util.mq.PropertyLoader;
import cn.gyyx.elves.util.zk.ZookeeperExcutor;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @ClassName: Storage
 * @Description: 内存 做 存储
 * @author East.F
 * @date 2016年12月5日 下午3:55:40
 *
 */
public class Storage {
	
	private static final Logger LOG= Logger.getLogger(Storage.class);
	
	/*
	 * agent 信息数据存储,key 为ip ,value为agent数据
	 */
	private static Map<String, CustomAgent> cache=new HashMap<String,CustomAgent>();
	
	/*
	 * 存放supervisor 管理的app信息（instruct,version）,定时同步+被动接收更新通知 (格式 [{'app':'testApp','version':'1.0','agentList':['192.168.1.1','192.168.1.2']})
	 */
	private static List<Map<String,Object>> cacheAppInfo = new ArrayList<Map<String,Object>>();
	
	/*
	 * heartbeat超时时间  6minute = 600 s = 60000 ms
	 */
	public static final long TIME_OUT=600000;

	/*
	 * agent 心跳时间为 60s = 60000ms
	 */
	public static final long AGENT_HEARTBEAT_TIME = 60000;

	/*
	 * 程序启动后，是否完成与zk 数据同步状态标识
	 */
	public static boolean syncZkFlag = false;

	/*
	 * zk heartbeat节点数据
	 */
	public static List<AgentInfo> zkHeartbeatData = new ArrayList<AgentInfo>();

	
	/**
	 * @Title: updateAppInfo
	 * @Description: 更新内存中app 数据
	 * @param appInfo 设定文件
	 * @return void    返回类型
	 */
	public static void updateAppInfo(List<Map<String,Object>> appInfo){
		cacheAppInfo.clear();
		cacheAppInfo.addAll(appInfo);
	}
	
	/**
	 * @Title: getAgentAppVersion
	 * @Description: 根据ip获取该机器上可以运行app列表
	 * @param ip
	 * @return 设定文件
	 * @return Map<String,String>    返回类型
	 */
	public static Map<String,String> getAgentAppVersion(String ip){
		if("supervisor".equalsIgnoreCase(PropertyLoader.AUTH_MODE)){
			Map<String,String> appVersion=new HashMap<String,String>();
			if(StringUtils.isNotBlank(ip)){
				for(Map<String,Object> app: cacheAppInfo){
					String instruct=app.get("app").toString();
					String version=app.get("version").toString();
					List<String> agentList = JSON.parseObject(app.get("agentList").toString(), new TypeReference<List<String>>(){});
					if(agentList.contains(ip)){
						appVersion.put(instruct, version);
						//break;
					}
				}
			}
			return appVersion;
		}else{
			return JSON.parseObject(PropertyLoader.AUTH_LOCAL_APP_INFO,new TypeReference<Map<String,String>>(){});
		}
	}
	
	public static List<Map<String, Object>> getCacheAppInfo() {
		return cacheAppInfo;
	}

	public static void setCacheAppInfo(List<Map<String, Object>> cacheAppInfo) {
		Storage.cacheAppInfo = cacheAppInfo;
	}

	/**
	 * @Title: put
	 * @Description: 存储
	 * @param info 设定文件
	 * @return void    返回类型
	 */
	public static void put(AgentInfo info){
		if(StringUtils.isNotBlank(info.getIp())){
			//判断是否要发起同步（如果AgentInfo并未改变则不发起同步操作）
			boolean doSync=true;
			CustomAgent custom = Storage.cache.get(info.getIp());
			if(null!=custom&&custom.equals(info)){
				doSync=false;
			}

			Storage.cache.put(info.getIp(), new CustomAgent(System.currentTimeMillis(),info));

			//同步更新在线机器信息到zk
			if(Storage.syncZkFlag&&doSync){
				syncCacheDataToZk();
			}
		}else{
			LOG.error("heartbeat module reveive elves-agent ip is empty"+info.toString());
		}
	}

	/**
	 * @Title: get
	 * @Description: 从cache获取在线的agent
	 * @param ip
	 * @param id
	 * @return 设定文件
	 * @return List<CustomAgent>    返回类型
	 */
	public static List<CustomAgent> get(String ip,String id){
		LOG.debug("storage:"+Storage.cache);
		List<CustomAgent> result=new ArrayList<CustomAgent>();
		if(StringUtils.isEmpty(ip)&&StringUtils.isEmpty(id)){
			for(CustomAgent custom:Storage.cache.values()){
				if(agentIsOnLine(custom)){
					result.add(custom);
				}
			}
			return result;
		}
		
		if(StringUtils.isNotBlank(ip)&&StringUtils.isNotBlank(id)){
			CustomAgent data=Storage.cache.get(ip);
			if(null!=data&&id.equals(data.getAgent().getId())&&agentIsOnLine(data)){
				result.add(data);
				return result;
			}
		}else if(StringUtils.isBlank(ip)&&StringUtils.isNotBlank(id)){
			for(CustomAgent custom:Storage.cache.values()){
				if(id.equals(custom.getAgent().getId())&&agentIsOnLine(custom)){
					result.add(custom);
					return result;
				}
			}
		}else if(StringUtils.isNotBlank(ip)&&StringUtils.isBlank(id)){
			CustomAgent data=Storage.cache.get(ip);
			if(agentIsOnLine(data)){
				result.add(data);
				return result;
			}
		}
		return result;
	}
	
	public static CustomAgent get(String ip){
		LOG.debug("storage:"+Storage.cache);
		if(StringUtils.isEmpty(ip)){
			return null;
		}
		CustomAgent agent = Storage.cache.get(ip);
		if(agentIsOnLine(agent)){
			return agent;
		}
		return null;
	}
	
	
	/**
	 * @Title: agentIsOnLine
	 * @Description: 检测agent是否已经在线
	 * @param custom
	 * @return boolean    返回类型
	 */
	private static boolean agentIsOnLine(CustomAgent custom){
		if(null==custom){
			return false;
		}
		long date=System.currentTimeMillis()-custom.getCheckTime();
		if(date<=Storage.TIME_OUT){
			return true;
		}
		return false;
	}
	
	public static Map<String, CustomAgent> getCache() {
		return cache;
	}

	public static void setCache(Map<String, CustomAgent> cache) {
		Storage.cache = cache;
	}


	/**
	 * 本地数据同步到zk
	 */
	public static void syncCacheDataToZk(){
		try {
			LOG.debug("start sync cache data to zk...");
			//本地内存中的在线数据(排序)
			List<AgentInfo> localData = new ArrayList<AgentInfo>();
			Iterator<CustomAgent> iterator = Storage.cache.values().iterator();
			while(iterator.hasNext()){
				CustomAgent custom= iterator.next();
				if(agentIsOnLine(custom)){
					localData.add(custom.getAgent());
				}
			}
			//排序
			Collections.sort(localData, new Comparator<AgentInfo>() {
				@Override
				public int compare(AgentInfo o1, AgentInfo o2) {
					return o1.getIp().compareTo(o2.getIp());
				}
			});

			if(!Storage.syncZkFlag){
				ZookeeperExcutor.client.setData().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/Heartbeat",JSON.toJSONString(localData).getBytes("UTF-8"));
				String zkData = new String(ZookeeperExcutor.client.getData().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/Heartbeat"),"UTF-8");
				if(StringUtils.isNotBlank(zkData)){
					Storage.zkHeartbeatData = JSON.parseObject(zkData, new TypeReference<List<AgentInfo>>(){});
				}
				//修改第一次同步状态
				Storage.syncZkFlag=true;
				LOG.debug("init sync localData to zk finish");
			}else{
				if(MD5Utils.MD5(JSON.toJSONString(localData)).equals(MD5Utils.MD5(JSON.toJSONString(Storage.zkHeartbeatData)))){
					ZookeeperExcutor.client.setData().forPath(PropertyLoader.ZOOKEEPER_ROOT+"/Heartbeat",JSON.toJSONString(localData).getBytes("UTF-8"));
					Storage.zkHeartbeatData = localData;
					LOG.debug("sync localData to zk finish");
				}else{
					LOG.debug("localData equals zk data");
				}
			}
		}catch (Exception e){
			LOG.error("syncCacheDataToZk error,msg:"+ ExceptionUtil.getStackTraceAsString(e));
		}
	}
}

package cn.gyyx.elves.heartbeat.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import cn.gyyx.elves.heartbeat.thrift.AgentInfo;
import cn.gyyx.elves.util.mq.PropertyLoader;

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
	private static Map<String,CustomAgent> cache=new HashMap<String,CustomAgent>();
	
	/*
	 * 存放supervisor 管理的app信息（instruct,version）,定时同步+被动接收更新通知 (格式 [{'app':'testApp','version':'1.0','agentList':['192.168.1.1','192.168.1.2']})
	 */
	private static List<Map<String,Object>> cacheAppInfo = new ArrayList<Map<String,Object>>();
	
	/*
	 * agent超时时间  60 s = 60000 ms
	 */
	private static final long TIME_OUT=600000;
	
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
			Storage.cache.put(info.getIp(), new CustomAgent(System.currentTimeMillis(),info));
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
	
}

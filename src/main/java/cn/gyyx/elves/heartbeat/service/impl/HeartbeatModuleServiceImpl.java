package cn.gyyx.elves.heartbeat.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;

import cn.gyyx.elves.heartbeat.service.HeartbeatModuleService;
import cn.gyyx.elves.heartbeat.thrift.AgentInfo;
import cn.gyyx.elves.heartbeat.thrift.HeartbeatService;
import cn.gyyx.elves.heartbeat.util.CustomAgent;
import cn.gyyx.elves.heartbeat.util.Storage;
import cn.gyyx.elves.util.DateUtils;
import cn.gyyx.elves.util.ExceptionUtil;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

/**
 * @ClassName: HeartbeatModuleServiceImpl
 * @Description: heartbeat模块服务实现类
 * @author East.F
 * @date 2016年12月5日 下午3:47:06
 */
@Service("elvesConsumerService")
public class HeartbeatModuleServiceImpl implements HeartbeatModuleService,HeartbeatService.Iface{
	
	private static final Logger LOG=Logger.getLogger(HeartbeatModuleServiceImpl.class);
	
	@Override
	public String heartbeatPackage(AgentInfo info) throws TException {
		LOG.debug("heartbeat module reveived agent heartbeat package:"+info);
		//更新agent的实时数据到内存中
		Storage.put(info);
		Map<String,Object> data=new HashMap<String,Object>();
		data.put("data",Storage.getAgentAppVersion(info.getIp()));
		String appData = JSON.toJSONString(data);
		LOG.debug("response apps data:"+appData);
		return appData;
	}
	
	@Override
	public Map<String, Object> agentInfo(Map<String, Object> params){
		LOG.debug("heartbeat module reveive getAgentInfo request params:"+params);
		Map<String, Object> rs=new HashMap<String, Object>();
		try {
			String ip=params.get("ip")==null?"":params.get("ip").toString();
			CustomAgent agent=Storage.get(ip);
			
			if(null==agent){
				rs.put("flag", "false");
				rs.put("error","[414.1]Agent Info Not Found");
				rs.put("result", new HashMap<String,Object>());
			}else{
				Map<String,Object> result=new HashMap<String,Object>();
				result.put("ip",agent.getAgent().getIp());
				result.put("asset",agent.getAgent().getId());
				result.put("last_hb_time",DateUtils.date2String(new Date(agent.getCheckTime()),DateUtils.DEFAULT_DATETIME_FORMAT));
				result.put("apps",JSON.parseObject(agent.getAgent().getApps(), new TypeReference<Map<String,Object>>(){}));
				
				rs.put("flag", "true");
				rs.put("error","");
				rs.put("result",result);
			}
		} catch (Exception e) {
			String error=ExceptionUtil.getStackTraceAsString(e);
			LOG.error("agentInfo error : "+error);
			rs.put("flag", "false");
			rs.put("error","[500]"+error);
			rs.put("result", new HashMap<String,Object>());
		}
		return rs;
	}
	
	@Override
	public Map<String, Object> agentList(Map<String, Object> params) {
		LOG.debug("heartbeat module reveive getAgentInfo request params:"+params);
		Map<String, Object> rs=new HashMap<String, Object>();
		try {
			String ip=params.get("ip")==null?"":params.get("ip").toString();
			String id=params.get("id")==null?"":params.get("id").toString();
			
			String pagesize=params.get("pagesize")==null?"":params.get("pagesize").toString();
			String pagenumber=params.get("pagenumber")==null?"":params.get("pagenumber").toString();
			
			List<CustomAgent> back=Storage.get(ip,id);
			List<Map<String,Object>> data=new ArrayList<Map<String,Object>>();
			for(CustomAgent agent:back){
				Map<String,Object> map=new HashMap<String,Object>();
				map.put("ip",agent.getAgent().getIp());
				map.put("asset",agent.getAgent().getId());
				map.put("last_hb_time",DateUtils.date2String(new Date(agent.getCheckTime()),DateUtils.DEFAULT_DATETIME_FORMAT));
				map.put("apps",JSON.parseObject(agent.getAgent().getApps(), new TypeReference<Map<String,Object>>(){}));
				data.add(map);
			}
			rs.put("count", back.size());
			rs.put("result", data);
		} catch (Exception e) {
			String error=ExceptionUtil.getStackTraceAsString(e);
			LOG.error("agentList error : "+error);
			 
		}
		return rs;
	}
	
	@Override
	public Map<String, Object> updateAppInfo(Map<String, Object> params){
		LOG.debug("heartbeat module reveive updateAppInfo msg to update app info, params:"+params);
		try {
			if(params.get("result")!=null){
				//更新appinfo 数据
				List<Map<String,Object>> data=JSON.parseObject(params.get("result").toString(),new TypeReference<List<Map<String,Object>>>(){});
				Storage.updateAppInfo(data);
			}
		} catch (Exception e) {
			LOG.error("updateAppInfo error : "+ExceptionUtil.getStackTraceAsString(e));
		}
		return null;
	}
}

package cn.gyyx.elves.heartbeat.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;

import cn.gyyx.elves.heartbeat.thrift.AgentInfo;
import cn.gyyx.elves.util.DateUtils;

/**
 * @ClassName: CustomAgent
 * @Description: 自定义agent,加入最后一次检测时间，用于标识在线状态，超时时间为1分钟
 * @author East.F
 * @date 2016年12月12日 上午11:21:32
 */
public class CustomAgent {

	private long checkTime;				//检测时间
	private AgentInfo agent;			//agent信息
	
	public CustomAgent(long checkTime, AgentInfo agent) {
		super();
		this.checkTime = checkTime;
		this.agent = agent;
	}
	
	public long getCheckTime() {
		return checkTime;
	}

	public void setCheckTime(long checkTime) {
		this.checkTime = checkTime;
	}

	public AgentInfo getAgent() {
		return agent;
	}

	public void setAgent(AgentInfo agent) {
		this.agent = agent;
	}
	
}

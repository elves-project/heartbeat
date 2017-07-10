package cn.gyyx.elves.heartbeat.util;

import cn.gyyx.elves.heartbeat.thrift.AgentInfo;

/**
 * @ClassName: CustomAgent
 * @Description: 自定义agent,加入最后一次检测时间，用于标识在线状态，超时时间为1分钟
 * @author East.F
 * @date 2016年12月12日 上午11:21:32
 */
public class CustomAgent {

	private long checkTime;				//检测时间
	private AgentInfo agent;			//agent信息

    public CustomAgent(){}

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

	@Override
	public boolean equals(Object obj) {
		if(null==obj||!(obj instanceof AgentInfo)){
			return false;
		}

		AgentInfo other=((CustomAgent)obj).getAgent();
		String ip =other.getIp();
		String id =other.getId();
		String version =other.getVersion();
		String apps =other.getApps();

		AgentInfo curr =getAgent();
		if(curr.getIp().equals(ip)&&curr.getId().equals(id)&&curr.getVersion().equals(version)&&curr.getApps().equals(apps)){
			return true;
		}
		return false;
	}
}

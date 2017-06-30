package cn.gyyx.elves.heartbeat.service;

import java.util.Map;

/**
 * @ClassName: HeartbeatModuleService
 * @Description: heartbeat模块服务接口
 * @author East.F
 * @date 2016年12月5日 下午3:50:43
 */
public interface HeartbeatModuleService {

	/**
	 * @Title: agentInfo
	 * @Description: 根据获取agent实时数据（返回单条数据）
	 * @param params
	 * @return Map<String,Object>    返回类型
	 */
	public Map<String, Object> agentInfo(Map<String, Object> params);
	
	/**
	 * @Title: agentList
	 * @Description: 搜索获取agent数据，可分页
	 * @param params
	 * @return Map<String,Object>    返回类型
	 */
	public Map<String, Object> agentList(Map<String, Object> params);
	
	
	/**
	 * @Title: noticeAppInfo
	 * @Description: 接受supervisor的通知， 更新内存中app版本信息发生变化
	 * @param params
	 * @throws Exception 设定文件
	 * @return Map<String,Object>    返回类型
	 */
	public Map<String, Object> updateAppInfo(Map<String, Object> params);
}

package cn.gyyx.elves.heartbeat.timer;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.gyyx.elves.heartbeat.util.Storage;
import cn.gyyx.elves.util.ExceptionUtil;
import cn.gyyx.elves.util.mq.MessageProducer;
import cn.gyyx.elves.util.mq.PropertyLoader;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

/**
 * @ClassName: AppInfoTasker
 * @Description: 定时器：如果SUPERVISR_SWITCH 开启，定时同步校验内存中app版本数据和supervisor是否一直，进行更新
 * @author East.F
 * @date 2017年5月23日 下午3:35:44
 */
@Component("appInfoTasker")
public class AppInfoTasker {
	
	private static final Logger LOG =Logger.getLogger(AppInfoTasker.class);
	
	@Autowired
	private MessageProducer messageProducer;
	
	public void excute(){
		try {
			LOG.info("Get app info tasker running,AUTH_MODE:"+PropertyLoader.AUTH_MODE);
			if("supervisor".equalsIgnoreCase(PropertyLoader.AUTH_MODE)){
				Map<String, Object> back = messageProducer.call("heartbeat.supervisor","appAuthInfo",null,5000);
				LOG.info("supervisor return data:"+back);
				if(null!=back&&null!=back.get("result")){
					List<Map<String, Object>> appInfo =  JSON.parseObject(back.get("result").toString(),new TypeReference<List<Map<String, Object>>>(){});
					Storage.updateAppInfo(appInfo);
				}
			}
			LOG.info("storage app info :"+Storage.getCacheAppInfo());
		} catch (Exception e) {
			LOG.error("update app info error:"+ExceptionUtil.getStackTraceAsString(e));
		}
	}
}

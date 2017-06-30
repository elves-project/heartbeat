package cn.gyyx.elves.heartbeat.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cn.gyyx.elves.util.MD5Utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

public class Test {

	
	public static void main(String[] args) {
		String authId="BD409EC9663DB93C";
		String authKey="C166E49697B1F8DE";
		
		String  gopApiUrl="http://gop-api-wan.gyyx.cn/centerapp/v1/base/asset_info";
		Map<String,Object> params=new HashMap<String, Object>();
		params.put("main_name","社区业务");
		//1.http 请求获取 资产列表																																																						
		String result=http(gopApiUrl, params);
		if (null==result||"".equals(result)) {
			System.out.println("error");
			return;																																																																																																																																																																																																																																	
		}
		Map<String, String> rs= JSON.parseObject(result,new TypeReference<Map<String,String>>(){});
		String data=rs.get("data");
		System.out.println(data);
		
		List<Map<String,Object>> dataList=JSON.parseObject(data, new TypeReference<List<Map<String,Object>>>(){});
		
		System.out.println(dataList.size());
		
		String openApiUrl ="http://elves-openapi.gyyx.cn";
		String createUri = "/api/queue/create";
		String subUrl="/api/queue/commit";
		
		//成功执行的 agent
		List<String> successAgent=new ArrayList<String>();
		//执行失败的agent
		List<String> failAgent=new ArrayList<String>();
		
		//创建成功的队列ID
		List<String> queueIds=new ArrayList<String>();
		
		
		//2. 循环创建队列
		for (int i = 0; i < dataList.size(); i++) {
			// 封装资产信息
			Map<String,String> paramMap=new HashMap<String,String>();
			paramMap.put("ResourceUrl","http://115.182.1.89/elves-agent-version-2.0.zip");
			paramMap.put("version","2.0");
			paramMap.put("assId",dataList.get(i).get("gysn").toString());
			
			Map<String,Object> pm=new HashMap<String,Object>();
			pm.put("agent_ip",dataList.get(i).get("ip").toString());
			pm.put("mode","sanp");
			pm.put("app","base");
			pm.put("func","AgentUpdate");
			pm.put("param",JSON.toJSONString(paramMap));
			
			pm.put("timestamp",System.currentTimeMillis()+"");
			pm.put("auth_id",authId);
			String sign=createSign(createUri, pm,authKey);
			pm.put("sign_type", "MD5");
			pm.put("sign", sign);
			
			System.out.println("pm:"+pm);
			String response=http(openApiUrl+createUri, pm);
			System.out.println("create queue response:"+response);
			
			Map<String, String> back= JSON.parseObject(response,new TypeReference<Map<String,String>>(){});
			String queueId=back.get("queue_id");
			
			if(null==queueId||"".equals(queueId)){
				failAgent.add(dataList.get(i).get("ip").toString());
			}else{
				queueIds.add(queueId);
				successAgent.add(dataList.get(i).get("ip").toString());
			}
		}
		
		//3.提交队列
		Map<String,Object> pm2=new HashMap<String,Object>();
		pm2.put("json_queue_ids", JSON.toJSON(queueIds));
		
		pm2.put("timestamp",System.currentTimeMillis()+"");
		pm2.put("auth_id",authId);
		String sign=createSign(subUrl, pm2,authKey);
		pm2.put("sign_type", "MD5");
		pm2.put("sign", sign);
		String response2=http(openApiUrl+subUrl, pm2);
		System.out.println(response2);
		
		System.out.println("success:"+successAgent);
		System.out.println("fail:"+failAgent);
	}
	
	
	
	public void dataList(){
		List<Map<String,String>> dataList=new ArrayList<>();
		
		Map<String,String> m=new HashMap<>();
		m.put("ip","115.182.1.64");// 123 121
		m.put("gysn","vm000279");
		dataList.add(m);
		Map<String,String> m2=new HashMap<>();
		m2.put("ip","115.182.1.240");// 123 121
		m2.put("gysn","vm000406");
		dataList.add(m2);
		Map<String,String> m3=new HashMap<>();
		m3.put("ip","192.168.6.52");// 123 121
		m3.put("gysn","GYDXT017");
		dataList.add(m3);
		
		Map<String,String> m4=new HashMap<>();
		m4.put("ip","192.168.6.53");// 123 121
		m4.put("gysn","GYS1050");
		dataList.add(m4);
		Map<String,String> m5=new HashMap<>();
		m5.put("ip","192.168.8.247");// 123 121
		m5.put("gysn","vm100122");
		dataList.add(m5);
		Map<String,String> m6=new HashMap<>();
		m6.put("ip","192.168.8.33");// 123 121
		m6.put("gysn","vm100481");
		dataList.add(m6);
		
		Map<String,String> m7=new HashMap<>();
		m7.put("ip","221.228.201.252");// 123 121
		m7.put("gysn","vm100256");
		dataList.add(m7);
		
		Map<String,String> m8=new HashMap<>();
		m8.put("ip","221.228.201.253");// 123 121
		m8.put("gysn","vm100257");
		dataList.add(m8);
	}
	
	public static String createSign(String uri,Map<String,Object> params,String authKey){
		StringBuffer sortUri=new StringBuffer(uri);
		sortUri.append("?");
		Set<String> keys=params.keySet();
		List<String> list=new ArrayList<String>();
		list.addAll(keys);
		Collections.sort(list);
		for(String k:list){
			if(!"sign_type".equals(k)&&!"sign".equals(k)&&null!=params.get(k)){
				sortUri.append(k+"="+params.get(k));
				sortUri.append("&");
			}
		}
		if(list.size()>0){
			sortUri.deleteCharAt(sortUri.length()-1);
		}
		sortUri.append(authKey);
		String signFinal=MD5Utils.MD5(sortUri.toString());
		return signFinal;
	}
	
	
	public static String http(String url, Map<String, Object> params) {
		URL u = null;
		HttpURLConnection con = null;
		// 构建请求参数
		StringBuffer sb = new StringBuffer();
		if (params != null) {
			for (Entry<String, Object> e : params.entrySet()) {
				if (null == e.getValue()) {
					continue;
				}
				sb.append(e.getKey());
				sb.append("=");
				sb.append(e.getValue());
				sb.append("&");
			}
			sb.substring(0, sb.length() - 1);
		}
		// 尝试发送请求
		try {
			u = new URL(url);
			con = (HttpURLConnection) u.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setDoInput(true);
			con.setUseCaches(false);
			con.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");
			OutputStreamWriter osw = new OutputStreamWriter(
					con.getOutputStream(), "utf-8");
			osw.write(sb.toString());
			osw.flush();
			osw.close();
			
			StringBuffer result = new StringBuffer();
			// 读取返回结果
			try {
				InputStream in = con.getInputStream();
				String line;
				BufferedReader br = new BufferedReader(new InputStreamReader(in,"utf-8"));
				while ((line = br.readLine()) != null) {
					result.append(line);
				}
				br.close();
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

			// JSONObject json =null;
			return result.toString();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
			if (con != null) {
				con.disconnect();
			}
		}
		
		return null;
	}
}

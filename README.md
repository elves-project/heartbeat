#elves-heartbeat#


##1.简介##

elves-heartbeat 提供agent上报在线数据服务，同时对外提供agent在线实时数据。

##2.服务##


###2.1 thrift服务###

thrift接口文件：

    //命令构体
	struct AgentInfo{
	    1 : i32 id,
	    2 : string ip,
	    3 : string apps
	}

	//HeartbeatService面对Agent的接口
	service HeartbeatService{
	    //异步返回结果处理器
	    string heartbeatPackage(1:AgentInfo info)
	}

	
解释说明：

	id  	中心应用资产ID
	ip		agent机器ip，格式：192.168.1.1
	apps	agent加载的app数据，格式 ｛"app1":"0.1","app2":"1.1",｝

###2.2 agent在线实时数据接口###


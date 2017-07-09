package cn.gyyx.elves.util.zk;

import cn.gyyx.elves.heartbeat.util.Storage;
import cn.gyyx.elves.util.ExceptionUtil;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @ClassName: ZookeeperExcutor
 * @Description: zookeeper连接处理器
 * @author East.F
 * @date 2016年11月7日 上午9:33:48
 */
public class ZookeeperExcutor {

	private static final Logger LOG=Logger.getLogger(ZookeeperExcutor.class);
	
	public static CuratorFramework client;

	public static void initClient(String zklist,int sessionTimeout,int connectTimeout){
		client = CuratorFrameworkFactory.builder()
				.connectString(zklist).sessionTimeoutMs(sessionTimeout)
				.connectionTimeoutMs(connectTimeout)
				.retryPolicy(new ExponentialBackoffRetry(1000, 3)).build();
		client.start();
	}

	//在注册监听器的时候，如果传入此参数，当事件触发时，逻辑由线程池处理
	static ExecutorService pool = Executors.newFixedThreadPool(20);
	
	/**
	 * @Title: createNodeAddListener
	 * @Description: 添加node节点
	 * @param nodePath
	 * @param nodeData 设定文件
	 * @return void    返回类型
	 */
	public static String createNode(String nodePath,String nodeData){
		if(client!=null){
			try {
				String nodeName=client.create().creatingParentsIfNeeded()
				.withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
				.forPath(nodePath, nodeData.getBytes("UTF-8"));
				return nodeName;
			} catch (UnsupportedEncodingException e) {
				LOG.error(ExceptionUtil.getStackTraceAsString(e));
				return null;
			} catch (Exception e) {
				LOG.error(ExceptionUtil.getStackTraceAsString(e));
				return null;
			}
		}
		return null;
	}
	/**
	 * @Title: getListener
	 * @Description: 为节点添加 connectState 监听器，实现断线重连，然后添加上节点
	 * @param nodePath	节点路径
	 * @param nodeData 节点数据
	 * @return void    返回类型
	 */
	public static ConnectionStateListener getListener(final String nodePath,final String nodeData){
		if(null!=client){
			ConnectionStateListener connectListener = new ConnectionStateListener() {
				@Override
				public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
					LOG.error("connectionState change,state:"+connectionState);
					if (connectionState == ConnectionState.LOST) {
						while (true) {
							try {
								//手动重连
								boolean flag=curatorFramework.getZookeeperClient().blockUntilConnectedOrTimedOut();
								if (flag){
									//重新添加节点
									clearListener();
									createNode(nodePath, nodeData);
									client.getConnectionStateListenable().addListener(getListener(nodePath, nodeData));
									break;
								}
							} catch (InterruptedException e) {
								LOG.error(ExceptionUtil.getStackTraceAsString(e));
							} catch (Exception e) {
								LOG.error(ExceptionUtil.getStackTraceAsString(e));
							}
						}
					}else if(connectionState==ConnectionState.RECONNECTED){
						//重新连接成功
					}else if(connectionState==ConnectionState.SUSPENDED){
						//自动重连,自动新建 schedular的临时节点
					}
				}
				
			};
			return connectListener;
		}
		return null;
	}

	/**
	 * @Title: addNodeChildrenChangeListener
	 * @Description: client 的节点添加 children change 监听器，根据子节点的变化，更新内存中的数据
	 * @param nodePath
	 * @throws Exception 设定文件
	 * @return void    返回类型
	 */
	public static void addNodeChildrenChangeListener(String nodePath) throws Exception{
		@SuppressWarnings("resource")
		final PathChildrenCache childrenCache = new PathChildrenCache(client,nodePath, true);
		childrenCache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
		childrenCache.getListenable().addListener(
				new PathChildrenCacheListener() {
					@Override
					public void childEvent(CuratorFramework client,PathChildrenCacheEvent event) throws Exception {
						String path="";
						byte[] bt=null;
						String data="";
						switch (event.getType()) {
							case CHILD_ADDED:
								path=event.getData().getPath();
								bt=client.getData().forPath(path);
								if(null!=bt){
									data=new String(bt,"UTF-8");
								}
								Storage.updateCacheDataFromZk(0,path,data);
								break;
							case CHILD_REMOVED:
								path=event.getData().getPath();
								Storage.updateCacheDataFromZk(1,path,data);
								break;
							case CHILD_UPDATED:
								path=event.getData().getPath();
								byte[] bt2=client.getData().forPath(path);
								if(null!=bt2){
									data=new String(bt2,"UTF-8");
								}
								Storage.updateCacheDataFromZk(2,path,data);
								break;
							default:
								break;
						}
					}
				}, pool);
	}

	
	public static void clearListener(){
		ListenerContainer<ConnectionStateListener> list=(ListenerContainer<ConnectionStateListener>) client.getConnectionStateListenable();
		list.clear();
	}
	
	public static void addListener(String nodePath,String nodeData){
		client.getConnectionStateListenable().addListener(getListener(nodePath, nodeData));
	}
}

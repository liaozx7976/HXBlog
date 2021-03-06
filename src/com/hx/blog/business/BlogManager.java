package com.hx.blog.business;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletContext;

import com.hx.blog.action.BlogListAction;
import com.hx.blog.bean.Blog;
import com.hx.blog.bean.TagToBlogCnt;
import com.hx.blog.util.Constants;
import com.hx.blog.util.Tools;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BlogManager {

	// 2015.10.15 : 移除了tagList表, 因为这张表中的数据是冗余的, 根据blogList可以不难得到
	// 关联的对象, tagListSql, addedBlogIdToTagMap, deletedBlogIdToTagMap
		// 前台点击当前标签的时候, 显示标签面板
	
	// blogList, tagList, tag到tag对应的博客数的集合
	// 增加的播客的集合 , 增加的播客的顺序[需要维护], 更新的播客的集合, 更新sense, visited的播客集合,  删除的播客的集合
	// 增加的(blogId -> [tag])的映射, 删除的(blogId -> [tag])的映射
	// 是否需要初始化, "all", 同步用的对象
	private static Map<Integer, Blog> blogList = new ConcurrentHashMap<>();
	private static Map<String, List<Integer>> tagList = new ConcurrentHashMap<>();
	private static Set<TagToBlogCnt> tagToBlogCnt = new TreeSet<>();
	private static List<Blog> addedList = new ArrayList<>();
	private static Map<Integer, Blog> updatedList = new HashMap<>();
	private static Map<Integer, Blog> visitSenseUpdatedList = new ConcurrentHashMap<>();
	private static List<Blog> deletedList = new ArrayList<>();
	private static boolean needInit = true;
	public final static String ALL = "all";
	public static Object initLock = new Object();
	public static Object updateLock = new Object();
	// TagToBlogCnt临时对象 [所有的使用场景均为同一个对象的同步快中, 线程安全]
	// 当前blogId的计数器 [unsafe 线程安全]
	// 空的JSONObject
	private static TagToBlogCnt tagToBlogCntTmp = new TagToBlogCnt();
	private static AtomicInteger curBlogId = new AtomicInteger(0);
		
	// 各个更新的数据 
	// [0] 表示添加的播客记录数, [1] 表示添加的(标签 -> 播客)的记录数
	// [2] 表示删除的播客记录数, [3] 表示删除的(标签 -> 播客)的记录数
	// [4] 表示更新的播客的记录数
	public static int[] updated = new int[5];
	
	// 通过tag获取响应的数据列表
	// 获取tag对应过的blogId列表
	// 如果blogId列表为null  则返回空的result 
	// 倒序获取当前tag对应过的blog是为了使最新的播客显示在前面 [这里 之后详细介绍]
		// 然后 获取标签列表
		// 最后将标签列表, 和播客列表封装起来, 返回
	public static JSONObject getResByTag(String tag, int pageNo) throws IOException {
		List<Integer> blogIds = tagList.get(tag);
		JSONArray blogList_ = new JSONArray();
		int pageSum = 0;
		if(! Tools.isEmpty(blogIds) ) {
			pageSum = Tools.calcPageNums(blogIds.size(), Constants.blogPerPage);
			if(pageNo >=0 && pageNo <= pageSum) {
				int end = blogIds.size()-1 - ((pageNo-1) * Constants.blogPerPage);
				int startTmp = end - Constants.blogPerPage + 1;
				int start =  startTmp >= 0 ? startTmp : 0;
				JSONObject obj = new JSONObject();
				for(int i=end; i>=start; i--) {
					Blog blog = blogList.get(blogIds.get(i));
					if(blog != null) {
						blog.encapJSON(obj);
						blogList_.add(obj);
					}
					obj.clear();
				}
				obj = null;
			}
		}
		
		boolean foundTag = false;
		JSONArray tagList_ = new JSONArray();
		Iterator<TagToBlogCnt> tagToBlogIt = tagToBlogCnt.iterator();
		JSONObject obj = new JSONObject();
		while(tagToBlogIt.hasNext() ) {
			TagToBlogCnt tagToBlog = tagToBlogIt.next();
			tagToBlog.encapJSON(obj);
			if(tagToBlog.getTag().equals(tag) ) {
				foundTag = true;
			}
			tagList_.add(obj);
			obj.clear();
		}
		obj = null;
		
		String curTag = foundTag ? tag : Constants.defaultBlogTag;
		
		JSONObject res = new JSONObject();
		res.element("tagList", tagList_.toString() );
		res.element("blogList", blogList_.toString() );
		res.element("pageSum", pageSum );
		res.element("curTag", curTag);
		return res;
	}

	// 如果有必要的话, 初始化blogList, tagList
	public static void initIfNeeded() {
		if(needInit) {
			// 2015.10.15 之前的时候, 居然没有注意观察播客显示的顺序, 今天fix了, 需要用一个List维护播客的顺序
			List<Integer> blogListOrder = new ArrayList<>();
			synchronized (initLock) {
				if(needInit) {
					try {
						initBlogList(blogListOrder);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			if(needInit) {
				initTagListAndTagToBlogCnt(blogListOrder);
				needInit = false;
			}
		}
	}
	
	// 获取下一个blog的id [BlogPublishAction]
	public static int nextBlogId() {
		return curBlogId.incrementAndGet();
	}

	// 发布播客, 更新播客, 删除播客
	public static void publishBlog(Blog newBlog) {
		publishBlog0(newBlog);
		checkIfNeedFlush();
	}
	public static void reviseBlog(Blog newBlog) {
		reviseBlog0(newBlog);
		checkIfNeedFlush();
	}
	public static void deleteBlog(Blog newBlog) {
		deleteBlog0(newBlog);
		checkIfNeedFlush();
	}
	
	// 添加blog到 visitSenseUpdatedList [更新了顶踩, 或者visited]
	public static void addVisitSense(Blog blog) {
		visitSenseUpdatedList.put(blog.getId(), blog);
	}
	
	// 给blogIdx对应的播客增加了一条评论, 一条访问记录, 一个good / notGood
	public static void addComment(Integer blogIdx) {
		Blog blog = BlogManager.getBlog(blogIdx);
		blog.incCommentsNum();
		addVisitSense(blog);
	}
	public static void addVisited(Integer blogId) {
		Blog blog = BlogManager.getBlog(blogId);
		blog.incVisited();
		addVisitSense(blog);
	}
	
	// 通过id获取对应的blog [并更新该blog的使用频率]
	public static Blog getBlog(Integer id) {
		CommentManager.incBlogGetFrequency(id);
		return blogList.get(id);
	}
	
	// 获取更新的元素的个数 [忽略, 顶踩更新的播客]
	public static int getUpdated() {
		synchronized (updateLock) {
			return addedList.size() + updatedList.size() + deletedList.size();
		}
	}
	
	// 获取更新的顶踩 或者visited的blog的个数
	public static int getVistitedSensedUpdate() {
		return visitSenseUpdatedList.size();
	}	
	
	// 将更新的数据刷新到数据库
		// 刷新数据库期间, 不允许对数据库进行操作
		// 创建各个更新的的元素的副本, 然后清理存放更新元素的容器 [以便于尽快回复业务]
		// 之前是使用addList, reviseList, ... 等待刷新到数据库完毕再释放updateLock
		// 现在更新为建立addList, reviseList, ..的缓存, 缓存之后, 直接释放updateLock 
	public static void flushToDB() {
		int updated = getUpdated();
		if(updated > 0) {
			List<Blog> addedListTmp = new ArrayList<>(); 
			List<Blog> deletedListTmp = new ArrayList<>(); 
			Map<Integer, Blog> updatedListTmp = new HashMap<>();
			Map<Integer, Blog> visitSenseUpdatedListTmp = new HashMap<>();
			visitSenseUpdatedListTmp.putAll(visitSenseUpdatedList);
			visitSenseUpdatedList.clear();
			
			boolean needFlush = (! visitSenseUpdatedListTmp.isEmpty()) || (! addedList.isEmpty()) || (! updatedList.isEmpty()) || (! deletedList.isEmpty() );
			// 并发优化	add at 2016.03.28
				// 场景 : 两个线程同时到达此处, 虽然逻辑是正确的, 但是后者却会多进行获取updateLock, initLock[无效的操作]
			if(needFlush) {
				needFlush = (! addedList.isEmpty()) || (! updatedList.isEmpty()) || (! deletedList.isEmpty() );
				if(needFlush) {
					synchronized (updateLock) {
						needFlush = (! addedList.isEmpty()) || (! updatedList.isEmpty()) || (! deletedList.isEmpty() );
						if(needFlush) {
							addedListTmp.addAll(addedList);
							deletedListTmp.addAll(deletedList);
							updatedListTmp.putAll(updatedList);

							addedList.clear();
							deletedList.clear();
							updatedList.clear();
						}
					}
				}
				
				needFlush = (! visitSenseUpdatedListTmp.isEmpty()) || (! addedListTmp.isEmpty()) || (! updatedListTmp.isEmpty()) || (! deletedListTmp.isEmpty() );
				if(needFlush) {
					synchronized (initLock) {
						Connection con = null;
						try {
							con = Tools.getConnection(Tools.getProjectPath());
							flushAddedRecords(con, addedListTmp);
							flushRevisedRecords(con, updatedListTmp, visitSenseUpdatedListTmp);
							flushDeletedRecords(con, deletedListTmp);
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							if(con != null) {
								try {
									con.close();
								} catch (SQLException e) {
									e.printStackTrace();
								}
							}					
						}
					}
				}
			}
			
			Tools.log(BlogListAction.class, getFlushInfo() );
		}		
	}
	
	// 刷新更新了访问量, 顶踩的播客 的数据到数据库
		// 还有一个问题, 我们只考虑了flushToDB的情况下面的刷出updatedList修改的时候, 删除visitSenseUpdatedList中对应的播客 [避免多次对同一个blog进行修改 [多条sql][效率]] [参见flushRevisedRecords]
		// 而并没有考虑flushToDBForVisitedSensed的情况下面, 并没有删除updatedList中对应过的播客 [可能会对一个播客进行多次无意义的修改]
	public static void flushToDBForVisitedSensed(ServletContext servletContext) {
		int updated = getVistitedSensedUpdate();
		if(updated > 0) {
			Map<Integer, Blog> visitSenseUpdatedListTmp = new HashMap<>();
			visitSenseUpdatedListTmp.putAll(visitSenseUpdatedList);
			visitSenseUpdatedList.clear();
			
			synchronized (initLock) {
				Connection con = null;
				try {
					con = Tools.getConnection(Tools.getProjectPath(servletContext));			
					flushRevisedRecords(con, null, visitSenseUpdatedListTmp);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if(con != null) {
						try {
							con.close();
						} catch (SQLException e) {
							e.printStackTrace();
						}
					}					
				}
			}
		}		
	}
	
	// 根据播客的id和tag获取博客的索引
	public static int getBlogIdxByIdAndTag(Integer id, String tag) {
		List<Integer> blogIds = tagList.get(tag);
		if(blogIds == null) {
			return Constants.HAVE_NO_THIS_TAG;
		}
		
		return blogIds.indexOf(id);
	}
	
	// 通过idx 和tag获取对应的blogId
	public static int getBlogIdByIdxAndTag(int idx, String tag) {
		List<Integer> blogIds = tagList.get(tag);
		if(idx < 0) {
			return Constants.HAVE_NO_NEXT_IDX;
		}
		if(idx >= blogIds.size()) {
			return Constants.HAVE_NO_PREV_IDX;
		}
		
		return blogIds.get(idx).intValue();
	}
	
	// context destory的时候清理占用的内存
	public static void clear() {
		blogList.clear();
		tagList.clear();
		visitSenseUpdatedList.clear();
		blogList = null;
		tagList = null;
		visitSenseUpdatedList = null;
		
		synchronized (updateLock) {
			tagToBlogCnt.clear();
			addedList.clear();
			deletedList.clear();
			updatedList.clear();
			
			tagToBlogCnt = null;
			addedList = null;
			deletedList = null;
			updatedList = null;
			initLock = null;
			tagToBlogCntTmp = null;
			curBlogId = null;
			updated = null;
		}
		updateLock = null;
	}	
	
	// 初始化blogList, tagList, 以及tagToBlogCnt [TreeSet 维护顺序]
	private static void initBlogList(List<Integer> blogListOrder) throws Exception {
		Connection con = null;
		try {
			con = Tools.getConnection(Tools.getProjectPath());
			blogList.clear();
			tagList.clear();
			tagToBlogCnt.clear();
			PreparedStatement blogPS = con.prepareStatement(Constants.blogListSql);
			
			// 初始化blogList, tagList, tagToBlogCnt
			ResultSet blogRs = blogPS.executeQuery();
			int maxBlogId = -1;
			while(blogRs.next() ) {
				Blog blog = new Blog();
				blog.init(blogRs);
				blogList.put(blog.getId(), blog);
				blogListOrder.add(blog.getId() );
//				allBlogIds.add(blog.getId() );
				maxBlogId = Math.max(maxBlogId, blog.getId());
			}
			curBlogId = new AtomicInteger(maxBlogId);
		} finally {
			if(con != null) {
				con.close();
			}
		}
	}	
	
	// 根据blogList初始化tagList, 根据tagList初始化tagToBlogCnt
	// 注意 : 这里tagList的初始化没有涉及的数据库查询
	private static void initTagListAndTagToBlogCnt(List<Integer> blogListOrder) {
		// ------- getTagList with blogList -------- 
		for(Integer blogId : blogListOrder) {
			Blog curBlog = blogList.get(blogId);
			for(String tag : curBlog.getTags()) {
				if(tagList.containsKey(tag)) {
					tagList.get(tag).add(blogId);
				} else {
					List<Integer> blogs = new ArrayList<>();
					blogs.add(blogId);
					tagList.put(tag, blogs);
				}
			}
		}
		
		for(Entry<String, List<Integer>> entry : tagList.entrySet()) {
			tagToBlogCnt.add(new TagToBlogCnt(entry.getKey(), entry.getValue().size()) );
		}
	}
	
	// 添加了一个博客
	// 将播客添加到blogList
	// addedList, 添加更新的标签映射addedBlogIdToTagMap [用于之后刷新到数据库]
		// 并在tagToBlogCnt中更新  [业务]
	// 添加当前播客的所有的标签到各自的标签分组[tagList]
	private static void publishBlog0(Blog newBlog) {
		blogList.put(newBlog.getId(), newBlog);
		synchronized (updateLock) {
			addedList.add(newBlog);
			if(newBlog.getTags().size() > 0) {
				for(String tag : newBlog.getTags()) {
					updateTagCntInTagToBlogCnt(tag, true);
				}
			}
		}
		
		for(String tag : newBlog.getTags()) {
			updateBlogIdToTagList(newBlog, tag, true);
		}
		
		// ---------------------------------------------------
	}
	// 更新了一个blog的内容
	// 获取增加了的标签, 以及删除了的标签
	// 将增加的标签添加到添加的标签映射 addedBlogIdToTagMap [用于之后刷新到数据库]
		// 并在tagToBlogCnt中更新 	[业务]
	// 将删除的标签添加到添加的标签映射 deletedBlogIdToTagMap [用于之后刷新到数据库]
		// 并在tagToBlogCnt中更新 	[业务]
	// 添加当前播客的所有的增加标签到各自的标签分组[tagList]
	// 删除当前播客的所有的删除标签到各自的标签分组[tagList]
	private static void reviseBlog0(Blog newBlog) {
		Blog oldBlog = blogList.put(newBlog.getId(), newBlog);
		
		List<String> addedTags = new ArrayList<>(newBlog.getTags() );
		addedTags.removeAll(oldBlog.getTags() );
		List<String> removedTags = new ArrayList<>(oldBlog.getTags() );
		removedTags.removeAll(newBlog.getTags() );
		// NPE at "(! oldBlog.getContent().equals(newBlog.getContent())"  at 2016.09.03
//		boolean isRevised = false;
//		isRevised = (! oldBlog.getTitle().equals(newBlog.getTitle()) ) || (! oldBlog.getContent().equals(newBlog.getContent()) );
		
		boolean isChangeName = (! Tools.equalsIgnorecase(oldBlog.getTitle().trim(), newBlog.getTitle().trim()));
		boolean isTagsUpdate = (addedTags.size() > 0) || (removedTags.size() > 0);
		if(isChangeName || isTagsUpdate) {
			synchronized (updateLock) {
//				// 短路或的特性, 如果 isRevised, 则说明, 没有tags的更新
				// 但是不能说明 ! isRevised => 存在tags更新				--2016.09.03
//				if(! isRevised) {
				for(String tag : addedTags) {
//					addedTagsOfBlog.add(tag);
					updateTagCntInTagToBlogCnt(tag, true);
				}
				for(String tag : removedTags) {
					updateTagCntInTagToBlogCnt(tag, false);
				}
//				}
				// 如果更新了名称, 或者tags, 则将其添加到更新列表中, 之后持久化到数据库		--2016.10.04
				updatedList.put(newBlog.getId(), newBlog);
			}
			
			for(String tag : addedTags) {
				updateBlogIdToTagList(newBlog, tag, true);
			}
			for(String tag : removedTags) {
				updateBlogIdToTagList(newBlog, tag, false);
			}			
		}
		
		// ---------------------------------------------------
	}
	// 删除了一个博客
	// 将播客从blogList中删除
	// deletedList, 添加删除的标签映射deletedBlogIdToTagMap [用于之后刷新到数据库]
		// 并在tagToBlogCnt中更新  [业务]
	// 删除当前播客的所有的标签到各自的标签分组[tagList]	
	private static void deleteBlog0(Blog newBlog) {
		blogList.remove(newBlog.getId());
		if(newBlog.getTags().size() > 0) {
			synchronized (updateLock) {
				deletedList.add(newBlog);
				for(String tag : newBlog.getTags()) {
					updateTagCntInTagToBlogCnt(tag, false);
				}
			}
			
			for(String tag : newBlog.getTags()) {
				updateBlogIdToTagList(newBlog, tag, false);
			}
		}
		
		CommentManager.delete(newBlog);
		// ---------------------------------------------------
	}
	
	// 检查是否需要刷出缓存的数据到数据库
		// 如果超过了更新blog的阈值, 则将当前缓存的更新刷新到数据库
	private static void checkIfNeedFlush() {
//		Log.log("updated : " + getUpdated() );
		if(getUpdated() >= Constants.updateBlogThreashold) {
			flushToDB();
		}
	}	
	
	// 将newBlog 添加 / 删除 到tag对应的tagList中
	private static void updateBlogIdToTagList(Blog newBlog, String tag, boolean isAdd) {
		List<Integer> listOfTag = tagList.get(tag);
		// need double check again ?? oh my god, need another lock.. 	--2016.10.04 
		if(listOfTag == null) {
			listOfTag = new ArrayList<>();
			tagList.put(tag, listOfTag);
		}
		
		if(isAdd) {
			listOfTag.add(newBlog.getId() );
		} else {
			listOfTag.remove(newBlog.getId() );
			if(listOfTag.size() == Constants.ZERO) {
				tagList.remove(tag);
			}
		}
	}
	
	// 为给定的tag的播客数量+1 / -1
		// 注意 tagToBlogCntTmp均是同一个对象的同步块中调用的, 所以是线程安全的
	private static void updateTagCntInTagToBlogCnt (String tag, boolean isAdd) {
		List<Integer> listOfTag = tagList.get(tag);
		if(listOfTag == null) {
			listOfTag = new ArrayList<>();
			tagList.put(tag, listOfTag);
		}
		
		tagToBlogCntTmp.setTag(tag);
		tagToBlogCntTmp.setBlogCnt(listOfTag.size());
		tagToBlogCnt.remove(tagToBlogCntTmp );
		if(isAdd) {
			tagToBlogCntTmp.incTagCnt();
		} else {
			tagToBlogCntTmp.decTagCnt();
		}
		if(! tagToBlogCntTmp.getBlogCnt().equals(Constants.INTE_ZERO) ) {
			tagToBlogCnt.add(new TagToBlogCnt(tagToBlogCntTmp) );
		}
	}	
	
	// 刷新添加的记录到db [blogList, tagList]
	private static void flushAddedRecords(Connection con, List<Blog> addedList) throws Exception {
		updated[Constants.addBlogCnt] = 0;
		updated[Constants.addTagCnt] = 0;
		if((addedList != null) && (addedList.size() > 0) ) {
			String addSelectedSql = Tools.getAddSelectedBlogsSql(addedList);
			Tools.log(BlogManager.class, addSelectedSql);
			PreparedStatement delBlogPs = con.prepareStatement(addSelectedSql);
			updated[Constants.addBlogCnt] = delBlogPs.executeUpdate();
		}
//		Tools.log(BlogListAction.class, "added " + updated[Constants.addBlogCnt] + " blogRecoreds to db , added " + updated[Constants.addTagCnt] + " tagRecoreds to db !");
	}	
	
	// 刷新删除的记录到db [blogList, tagList]
	private static void flushDeletedRecords(Connection con, List<Blog> deletedList) throws Exception {
		updated[Constants.deletedBlogCnt] = 0;
		updated[Constants.deletedTagCnt] = 0;
		if((deletedList != null) && (deletedList.size() > 0) ) {
			String deleteSelectedSql = Tools.getDeleteSelectedBlogsSql(deletedList);
			Tools.log(BlogManager.class, deleteSelectedSql);
			PreparedStatement delBlogPs = con.prepareStatement(deleteSelectedSql);
			updated[Constants.deletedBlogCnt] = delBlogPs.executeUpdate();
		}
//		Tools.log(BlogListAction.class, "deleted " + updated[Constants.deletedBlogCnt] + " blogRecoreds int db , deleted " + updated[Constants.deletedTagCnt] + " tagRecoreds int db !");
	}
	
	// 刷新更新的记录到db [blogList]
	private static void flushRevisedRecords(Connection con, Map<Integer, Blog> updatedList, Map<Integer, Blog> visitSenseUpdatedListTmp) throws Exception {
		updated[Constants.revisedBlogCnt] = 0;
		if((updatedList != null) && (updatedList.size() > 0) ) {
			String updateSelectedSql = null;
			PreparedStatement updateTagsPs = null;
			for(Entry<Integer, Blog> entry : updatedList.entrySet()) {
				visitSenseUpdatedListTmp.remove(entry.getKey() );
				updateSelectedSql = Tools.getUpdateBlogListSql(entry.getKey(), entry.getValue());
				Tools.log(BlogManager.class, updateSelectedSql);
				updateTagsPs = con.prepareStatement(updateSelectedSql);
				updated[Constants.revisedBlogCnt] += updateTagsPs.executeUpdate();
			}
		}
		
		if((visitSenseUpdatedListTmp != null) && (visitSenseUpdatedListTmp.size() > 0) ) {
			String updateSelectedSql = null;
			PreparedStatement updateTagsPs = null;
			for(Entry<Integer, Blog> entry : visitSenseUpdatedListTmp.entrySet()) {
				updateSelectedSql = Tools.getUpdateBlogListSql(entry.getKey(), entry.getValue());
				Tools.log(BlogManager.class, updateSelectedSql);
				updateTagsPs = con.prepareStatement(updateSelectedSql);
				updated[Constants.revisedBlogCnt] += updateTagsPs.executeUpdate();
			}
		}
		
//		Tools.log(BlogListAction.class, "updated " + updated[Constants.revisedBlogCnt] + " blogRecoreds to db !");
	}
	
	// 获取更新的记录信息 [日志]
	private static String getFlushInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append("addBlog : ");	sb.append(updated[Constants.addBlogCnt] );
		sb.append(", addTag : ");	sb.append(updated[Constants.addTagCnt] );
		sb.append(", revisedBlog : ");	sb.append(updated[Constants.revisedBlogCnt] );
		sb.append(", deletedBlog : ");	sb.append(updated[Constants.deletedBlogCnt] );
		sb.append(", deletedTag : ");	sb.append(updated[Constants.deletedTagCnt] );
		
		return sb.toString();
	}

}

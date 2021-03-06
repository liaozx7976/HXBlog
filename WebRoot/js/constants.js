    // -------------------- 数据结构相关--------------------------
	// js实现StringBuilder
	function StringBuilder() {
		this._stringArray = new Array();
	}
	StringBuilder.prototype.append = function(str){
		this._stringArray.push(str);
	}
	StringBuilder.prototype.toString = function(sep){
		return this._stringArray.join(sep);
	}
	
	// blogBean
	function Blog(id, title, tags, checkCode, content) {
		this.id = id
		this.title = title
		this.tags = tags
		this.checkCode = checkCode
		this.content = content
	}
	Blog.prototype.toString = function() {
		var sb = new StringBuilder()
		sb.append("{ ")
		sb.append("id : \"")
		sb.append(this.id)		
		sb.append("\", title : \"")
		sb.append(this.title)
		sb.append("\", tags : \"")
		sb.append(this.tags)		
		sb.append("\", checkCode : \"")
		sb.append(this.checkCode)				
		sb.append("\", content : \"")
		sb.append(this.content)		
		sb.append("\" }")
		
		return sb.toString(EMPTY_STR)
	}
	Blog.prototype.getObj = function() {
		var blogObj = {
				"id" : this.id,
				"title" : this.title,
				"tags" : this.tags,
				"checkCode" : this.checkCode,
				"content" : this.content
		}
		
		return blogObj
	}	
	
	// commentBean
	function Comment(blogIdx, floorIdx, userName, to, email, imageIdx, comment) {
		this.blogIdx = blogIdx
		this.floorIdx = floorIdx
//		this.commentIdx = commentIdx
		this.userName = userName
		this.to = to
		this.email = email
		this.imageIdx = imageIdx
		this.comment = comment
	}
	Comment.prototype.toString = function() {
		var sb = new StringBuilder()
		sb.append("{ ")
		sb.append("blogIdx : \"")
		sb.append(this.blogIdx)		
		sb.append("\", floorIdx : \"")
		sb.append(this.floorIdx)		
//		sb.append("\", commentIdx : \"")
//		sb.append(this.commentIdx)			
		sb.append("\", userName : \"")
		sb.append(this.userName)		
		sb.append("\", to : \"")
		sb.append(this.to)			
		sb.append("\", email : \"")
		sb.append(this.email)
		sb.append("\", imageIdx : \"")
		sb.append(this.imageIdx)		
		sb.append("\", comment : \"")
		sb.append(this.comment)		
		sb.append("\" }")
		
		return sb.toString(EMPTY_STR)
	}
	Comment.prototype.getObj = function() {
		var blogObj = {
				"blogIdx" : this.blogIdx,
				"floorIdx" : this.floorIdx,
//				"commentIdx" : this.commentIdx,
				"userName" : this.userName,
				"to" : this.to,
				"email" : this.email,
				"imageIdx" : this.imageIdx,
				"comment" : this.comment
		}
		
		return blogObj
	}		
	
	
	// 常量
	var EMPTY_STR = ""
	var NULL = "null"
	var UNDEFINED = "undefined"
	var HTML = ".html"
	var tagSep = ","
	var maxTagNum = 10
	var maxTagLength = 20
	var specCharReg = "[!-/ | :-@ | {-~ | \\[ /\\]^—、]"
//	var haveNoPrevBlogIdx = -1
//	var haveNoNextBlogIdx = -2
	
	// 通用的方法
	// 判断给定的name是否满足regExp
	function isMatch(regExp, nickName){
	    var reg = new RegExp(regExp);
	    return reg.test(nickName);
	}
	
	// 获取str中最后一个sep分隔符之后的数据
	function getStrAfterLastSep(str, sep) {
		var lastIdx = str.lastIndexOf(sep)
		if(lastIdx >= 0) {
			return str.substr(lastIdx + sep.length);
		} else {
			return null;
		}
	}	
	
	// 校验给定的字符串是否为空
	function isEmpty(content) {
		return (typeof(content) == UNDEFINED) || (content == null) || (content.length == 0) || (content.trim() == EMPTY_STR)
	}
	
    // 校验title
    function validateTitle(title, titleStr, noticePath) {
    	if(isEmpty(title) ) {
    		$(noticePath).html(titleStr + " can't be empty !")
    		return false
    	} 	
    	
    	return true
    }
    
    // 校验tag
    function validateTag(tag, noticePath) {
    	if(tag == EMPTY_STR) {
//    		$(noticePath).html("tag can't be empty !")
    		return false
    	}
    	if(tag.length >= maxTagLength) {
    		$(noticePath).html("tag is too long !")
    		return false
    	}
    	if(isMatch(specCharReg, tag)) {
    		$(noticePath).html("tag can't contains spec character [eg : ! - /] !")
    		return false
    	}
    		
    	return true
    }
    
    // 校验tags
    function validateTags(tagsPath, tag, noticePath) {
    	var tags = $(tagsPath)
    	// "tags" include another "tags span", so use ">"
    	if(tags.length > maxTagNum) {
    		$(noticePath).html("tag is to much !")    	
    		return false
    	}    
    	var isExists = isTagExists(tags, tag)
    	if(isExists) {
    		$(noticePath).html("this tag already exists !")    	
    		return false
    	}
    	
    	return true
    }
    
    // 校验输入内容
    function validateContent(content, contentStr, noticePath) {
    	if(isEmpty(content) ) {
    		$(noticePath).html(contentStr + " can't be empty !")
    		return false
    	}
    	
    	return true
    }	
    
	// 获取给定的对象的长度
	function getLength(o){
	   var n, count = 0;
	   for(n in o){
	      if(o.hasOwnProperty(n)){
	         count++;
	      }
	   }

	   return count;
	}
    
    // 添加一楼的评论
    function appendNewFloorComment(commentPath, comment) {
    	$(commentPath).last().after(
    			"<hr />" +
				"<dl class='comment_item comment_topic' >" +
				"<dt class='comment_head'>" +
					"<span id='floorId' >" + comment.floorIdx + "楼</span>&nbsp;&nbsp;&nbsp;&nbsp;" + 
					"<span class='user' floorIdx='" + comment.floorIdx + "' commentIdx='" + comment.commentIdx + "' userName='" + comment.userInfo.name + "' >" +
						"<span id='commenter' class='text-warning' >" + comment.userInfo.name + "</span>&nbsp;&nbsp;&nbsp;&nbsp;" +
						"<span id='privilege' class='text-error' >[" + comment.userInfo.privilege + "]</span>&nbsp;&nbsp;&nbsp;&nbsp;" +
						"<span id='date' class='text-success'>发表于  " + comment.date + "</span>&nbsp;&nbsp;&nbsp;&nbsp;" +
						"<span class='comment_manage' > " +
							"<span class='commentReply' ><a href='javascript:void(0)' class='cmt_btn reply' title='回复'>[回复]</a></span>&nbsp;&nbsp;" +
							"<a href='javascript:void(0)' class='cmt_btn quote' title='引用'>[引用]</a>&nbsp;&nbsp;" +
							"<a href='javascript:void(0)' class='cmt_btn report' title='举报'>[举报]</a>&nbsp;&nbsp;" +
						"</span>" +
					"</span>" +
				"</dt>" +
				
				"<dd class='comment_userface'>" +
					"<a href='javascript:void(0)' > <img src='./images/avatar/btn_avatar_a" + comment.userInfo.imageIdx + ".png' width='40' height='40' title='" + comment.userInfo.email + "' /> </a> " +
				"</dd>  :"+ 
				"<dd class='comment_body'>" + comment.comment + "</dd>" +
				
				"<div class='replyDiv comment_reply' > </div>" +
				"</dl>"
			)
    }
    
    // 添加回复的评论 [与上一个评论存在offset]
    function appendNewReplyComment(commentPath, floorIdx, replyDivPath, comment) {
//		var eles = $("dl.comment_topic").get(0).getElementsByClassName("replyDiv")
//		console.log(eles[eles.length-1])
//		eles[eles.length-1].innerHTML = 
		// 通过索引获取请使用.eq(idx), get方法获取的是dom元素
		$(commentPath).eq(floorIdx).find(replyDivPath).last().html(
				"<dl class='comment_item'>" +
				"<dt class='comment_head' >" + 
				"Re: " + 
				"<span class='user' floorIdx='" + comment.floorIdx + "' commentIdx='" + comment.commentIdx + "' userName='" + comment.userInfo.name + "' > " +
					"<span id='commenter' class='text-warning' >" + comment.userInfo.name + "</span>&nbsp;&nbsp;&nbsp;&nbsp;" +
					"<span id='privilege' class='text-error' >[" + comment.userInfo.privilege + "]</span>&nbsp;&nbsp;&nbsp;&nbsp;" +
					"<span id='date' class='text-success'>发表于  " + comment.date + "</span>&nbsp;&nbsp;&nbsp;&nbsp; " +  
					"<span class='comment_manage' > " +
						"<span class='commentReply' ><a href='javascript:void(0)' class='cmt_btn reply' title='回复'>[回复]</a></span>&nbsp;&nbsp;" +
						"<a href='javascript:void(0)' class='cmt_btn quote' title='引用'>[引用]</a>&nbsp;&nbsp;" + 
						"<a href='javascript:void(0)' class='cmt_btn report' title='举报'>[举报]</a>&nbsp;&nbsp;" +
					"</span>" +
				"</span>" +
				"</dt>" +
				"<dd class='comment_userface'>" +
					"<a href='javascript:void(0)'><img src='./images/avatar/btn_avatar_a" + comment.userInfo.imageIdx + ".png' width='40' height='40' title='" + comment.userInfo.email + "' ></a>" +
				"</dd>" +
				"<dd class='comment_body'>回复 " + comment.to + " : " + comment.comment + "</dd>" +
				
				"<div class='replyDiv comment_reply' > </div>" +
				"</dl>" 
				)
    }
    
	
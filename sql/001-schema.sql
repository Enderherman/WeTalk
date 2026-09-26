-- WeTalk empty-database initialization; exported from actual local MySQL.
-- Table definitions only: no accounts, password hashes, messages or local data.
-- Execute only against a new empty database. This is not an upgrade script.


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_update` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `version` varchar(10) DEFAULT NULL COMMENT '版本号',
  `update_desc` varchar(500) DEFAULT NULL COMMENT '更新信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` tinyint(1) DEFAULT NULL COMMENT '0:未发布 1:灰度发布 2:全部发布',
  `grayscale_uid` varchar(1000) DEFAULT NULL COMMENT '灰度uid',
  `file_type` tinyint(1) DEFAULT NULL COMMENT '文件类型 0:本地文件 1:外链',
  `outer_link` varchar(200) DEFAULT NULL COMMENT '外链地址',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='app发布表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `message_id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `session_id` varchar(32) NOT NULL COMMENT '会话id',
  `message_type` tinyint(1) NOT NULL COMMENT '消息类型',
  `message_content` varchar(500) DEFAULT NULL COMMENT '消息内容',
  `send_user_id` varchar(12) DEFAULT NULL COMMENT '发送人id',
  `send_user_nick_name` varchar(20) DEFAULT NULL COMMENT '发送人昵称',
  `send_time` bigint DEFAULT NULL COMMENT '发送时间',
  `contact_id` varchar(12) NOT NULL COMMENT '联系人id',
  `contact_type` tinyint(1) DEFAULT NULL COMMENT '联系人类型 0:单聊 1:群聊',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `file_name` varchar(200) DEFAULT NULL COMMENT '文件名',
  `file_type` tinyint(1) DEFAULT NULL COMMENT '文件类型',
  `status` tinyint(1) DEFAULT NULL COMMENT '状态 0:正在发送 1:已发送',
  PRIMARY KEY (`message_id`),
  KEY `idx_session_id` (`session_id`) USING BTREE,
  KEY `idx_send_user_id` (`send_user_id`) USING BTREE,
  KEY `idx_receive_contact_id` (`contact_id`) USING BTREE,
  KEY `idx_send_time` (`send_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_session` (
  `session_id` varchar(32) NOT NULL COMMENT '会话id',
  `last_message` varchar(500) DEFAULT NULL COMMENT '最后接收的消息',
  `last_receive_time` bigint DEFAULT NULL COMMENT '最后接收消息时间(毫秒)',
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_session_user` (
  `user_id` varchar(12) NOT NULL COMMENT '用户id',
  `contact_id` varchar(12) NOT NULL COMMENT '联系人id',
  `session_id` varchar(32) NOT NULL COMMENT '会话id',
  `contact_name` varchar(20) DEFAULT NULL COMMENT '联系人名称',
  PRIMARY KEY (`user_id`,`contact_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_session_id` (`session_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_info` (
  `group_id` varchar(12) NOT NULL COMMENT '群组id',
  `group_name` varchar(32) DEFAULT NULL COMMENT '群组昵称',
  `group_own_id` varchar(12) DEFAULT NULL COMMENT '群主用户id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `group_notice` varchar(500) DEFAULT NULL COMMENT '创建时间',
  `join_type` tinyint(1) DEFAULT NULL COMMENT '加入方式: 0:直接加入 1:管理员同意后加入',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态： 0:解散 1:正常',
  PRIMARY KEY (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='群组信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_contact` (
  `user_id` varchar(12) NOT NULL COMMENT '用户id',
  `contact_id` varchar(12) NOT NULL COMMENT '联系人id或者群组id',
  `contact_type` tinyint(1) DEFAULT NULL COMMENT '联系人类型: 0:好友 1:群组',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` tinyint(1) DEFAULT NULL COMMENT '状态: 0:非好友 1:好友 2:已删除好友 3:被好友删除 4:已拉黑好友 5:被好友拉黑',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`user_id`,`contact_id`) USING BTREE,
  KEY `idx_contact_id` (`contact_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联系人表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_contact_apply` (
  `apply_id` int NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `apply_user_id` varchar(12) NOT NULL COMMENT '申请人id',
  `receive_user_id` varchar(12) NOT NULL COMMENT '接收人id',
  `contact_type` tinyint(1) NOT NULL COMMENT '联系人类型 0:好友 1:群组',
  `contact_id` varchar(12) DEFAULT NULL COMMENT '联系人id或群组id 取决于加人还是加好友',
  `last_apply_time` bigint DEFAULT NULL COMMENT '最后申请时间',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态 0:待处理 1:已同意 2:已拒绝 3:已拉黑',
  `apply_info` varchar(100) DEFAULT NULL COMMENT '申请信息',
  PRIMARY KEY (`apply_id`),
  UNIQUE KEY `idx_key` (`apply_user_id`,`receive_user_id`,`contact_id`),
  KEY `idx_last_apply_time` (`last_apply_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联系人申请表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_info` (
  `user_id` varchar(12) NOT NULL COMMENT '用户id',
  `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
  `nick_name` varchar(40) DEFAULT NULL COMMENT '昵称',
  `join_type` tinyint(1) DEFAULT NULL COMMENT '添加好友方式: 0:直接添加,1:同意后添加',
  `sex` tinyint(1) DEFAULT NULL COMMENT '性别:0男1女',
  `password` varchar(32) DEFAULT NULL COMMENT '密码',
  `personal_signature` varchar(64) DEFAULT NULL COMMENT '个性签名',
  `status` tinyint(1) DEFAULT NULL COMMENT '状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `area_name` varchar(64) DEFAULT NULL COMMENT '地区',
  `area_code` varchar(64) DEFAULT NULL COMMENT '地区编号',
  `last_off_time` bigint DEFAULT NULL COMMENT '最后离开时间',
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `idx_key_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_info_beauty` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `email` varchar(50) NOT NULL COMMENT '邮箱',
  `user_id` varchar(12) NOT NULL COMMENT '用户id',
  `status` tinyint(1) DEFAULT NULL COMMENT '0:未使用 1:已使用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_key_email` (`email`),
  UNIQUE KEY `idx_key_id` (`id`),
  UNIQUE KEY `idx_key_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='靓号表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

package top.enderherman.wetalk.utils;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.enums.UserContactTypeEnum;
import top.enderherman.wetalk.exception.BusinessException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;


public class StringUtils {

    public static void checkParam(Object param) {
        try {
            Field[] fields = param.getClass().getDeclaredFields();
            boolean notEmpty = false;
            for (Field field : fields) {
                String methodName = "get" + StringUtils.upperCaseFirstLetter(field.getName());
                Method method = param.getClass().getMethod(methodName);
                Object object = method.invoke(param);
                if (object != null && object instanceof String && !StringUtils.isEmpty(object.toString())
                        || object != null && !(object instanceof String)) {
                    notEmpty = true;
                    break;
                }
            }
            if (!notEmpty) {
                throw new BusinessException("多参数更新，删除，必须有非空条件");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException("校验参数是否为空失败");
        }
    }

    public static String upperCaseFirstLetter(String field) {
        if (isEmpty(field)) {
            return field;
        }
        //如果第二个字母是大写，第一个字母不大写
        if (field.length() > 1 && Character.isUpperCase(field.charAt(1))) {
            return field;
        }
        return field.substring(0, 1).toUpperCase() + field.substring(1);
    }

    public static boolean isEmpty(String str) {
        if (null == str || "".equals(str) || "null".equals(str) || "\u0000".equals(str)) {
            return true;
        } else return "".equals(str.trim());
    }

    /**
     * 生成用户id
     */
    public static String getUserId() {
        return UserContactTypeEnum.USER.getPrefix() + getRandomNumber(Constants.LENGTH_11);
    }

    /**
     * 生成群组id
     */
    public static String getGroupId() {
        return UserContactTypeEnum.GROUP.getPrefix() + StringUtils.getRandomNumber(Constants.LENGTH_11);
    }

    /**
     * 生成随机数
     *
     * @param length 长度
     * @return 随机数
     */
    public static String getRandomNumber(Integer length) {
        return RandomStringUtils.random(length, false, true);
    }

    /**
     * 生成随机码
     *
     * @param length 长度
     * @return 随机码
     */
    public static String getRandomString(Integer length) {
        return RandomStringUtils.random(length, true, true);
    }


    /**
     * md5编码
     *
     * @param originString 密码
     * @return 编码后的密码
     */
    public static String encodingByMd5(String originString) {
        return isEmpty(originString) ? null : DigestUtils.md5Hex(originString);
    }


    /**
     * 去除HTML注入
     */
    public static String cleanHtmlTag(String content) {
        if (isEmpty(content)) {
            return content;
        }
        content = content.replaceAll("<", "&lt;");
        content = content.replaceAll(">", "&gt;");
        content = content.replaceAll("\r\n", "<br>");
        content = content.replaceAll("\n", "<br>");
        content = content.replaceAll("\"", "&quot;");
        content = content.replaceAll("'", "&apos;");
        return content;
    }

    /**
     * 生成单独聊天会话id
     */
    public static String getChatSessionId4User(String[] userIds){
        Arrays.sort(userIds);
        return encodingByMd5(StringUtils.join(userIds,""));

    }


    /**
     * 生成群聊会话Id
     */
    public static String getChatSessionId4Group(String groupId) {
        return encodingByMd5(groupId);
    }

    /**
     * 拼接字符串
     */
    private static String join(String[] userIds, String replace) {
        if (userIds == null || userIds.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < userIds.length; i++) {
            sb.append(userIds[i]);
            if (i < userIds.length - 1) {
                sb.append(replace);
            }
        }
        return sb.toString();
    }


    /**
     * 获取文件后缀
     */
    public static String getFileSuffix(String fileName){
        if(fileName==null|| fileName.isEmpty()){
            return "";
        }
        int index = fileName.lastIndexOf(".");
        if (index == -1){
            return "";
        }
        String suffix = fileName.substring(index);
        return suffix;
    }

    /**
     * 校验是否是数字
     */
    public static boolean isNumber(String str){
        if (null == str) {
            return false;
        }
        String regex = "^[0-9]+$";
        return str.matches(regex);
    }

}

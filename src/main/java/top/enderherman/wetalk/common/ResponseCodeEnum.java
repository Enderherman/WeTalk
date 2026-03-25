package top.enderherman.wetalk.common;


import lombok.Getter;

@Getter
public enum ResponseCodeEnum {
    CODE_200(200, "请求成功"),
    CODE_404(404, "请求地址不存在"),
    CODE_500(500, "服务器返回错误，请联系管理员"),
    CODE_506(506, "序列化失败"),
    CODE_600(600, "请求参数错误"),
    CODE_601(601, "信息已经存在"),
    CODE_602(602, "文件不存在"),
    CODE_901(901, "登录超时"),
    CODE_902(902, "您还不是好友"),
    CODE_903(903, "尚未加入群聊");
    private final Integer code;

    private final String msg;

    ResponseCodeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}

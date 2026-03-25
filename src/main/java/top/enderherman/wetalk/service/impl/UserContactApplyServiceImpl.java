package top.enderherman.wetalk.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.entity.enums.PageSize;
import top.enderherman.wetalk.entity.enums.UserContactApplyStatusEnum;
import top.enderherman.wetalk.entity.enums.UserContactStatusEnum;
import top.enderherman.wetalk.entity.po.UserContact;
import top.enderherman.wetalk.entity.po.UserContactApply;
import top.enderherman.wetalk.entity.query.SimplePage;
import top.enderherman.wetalk.entity.query.UserContactApplyQuery;
import top.enderherman.wetalk.entity.query.UserContactQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.UserContactApplyMapper;
import top.enderherman.wetalk.mappers.UserContactMapper;
import top.enderherman.wetalk.service.UserContactApplyService;
import top.enderherman.wetalk.service.UserContactService;
import top.enderherman.wetalk.utils.StringUtils;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;


/**
 * 联系人申请表 业务接口实现
 */
@Service("userContactApplyService")
public class UserContactApplyServiceImpl implements UserContactApplyService {

    @Resource
    private UserContactApplyMapper<UserContactApply, UserContactApplyQuery> userContactApplyMapper;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private UserContactService userContactService;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserContactApply> findListByParam(UserContactApplyQuery param) {
        return this.userContactApplyMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserContactApplyQuery param) {
        return this.userContactApplyMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserContactApply> findListByPage(UserContactApplyQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserContactApply> list = this.findListByParam(param);
        PaginationResultVO<UserContactApply> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserContactApply bean) {
        return this.userContactApplyMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserContactApply> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userContactApplyMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserContactApply> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userContactApplyMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserContactApply bean, UserContactApplyQuery param) {
        StringUtils.checkParam(param);
        return this.userContactApplyMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserContactApplyQuery param) {
        StringUtils.checkParam(param);
        return this.userContactApplyMapper.deleteByParam(param);
    }

    /**
     * 根据ApplyId获取对象
     */
    @Override
    public UserContactApply getUserContactApplyByApplyId(Integer applyId) {
        return this.userContactApplyMapper.selectByApplyId(applyId);
    }

    /**
     * 根据ApplyId修改
     */
    @Override
    public Integer updateUserContactApplyByApplyId(UserContactApply bean, Integer applyId) {
        return this.userContactApplyMapper.updateByApplyId(bean, applyId);
    }

    /**
     * 根据ApplyId删除
     */
    @Override
    public Integer deleteUserContactApplyByApplyId(Integer applyId) {
        return this.userContactApplyMapper.deleteByApplyId(applyId);
    }

    /**
     * 根据ApplyUserIdAndReceiveUserIdAndContactId获取对象
     */
    @Override
    public UserContactApply getUserContactApplyByApplyUserIdAndReceiveUserIdAndContactId(String applyUserId, String receiveUserId, String contactId) {
        return this.userContactApplyMapper.selectByApplyUserIdAndReceiveUserIdAndContactId(applyUserId, receiveUserId, contactId);
    }

    /**
     * 根据ApplyUserIdAndReceiveUserIdAndContactId修改
     */
    @Override
    public Integer updateUserContactApplyByApplyUserIdAndReceiveUserIdAndContactId(UserContactApply bean, String applyUserId, String receiveUserId, String contactId) {
        return this.userContactApplyMapper.updateByApplyUserIdAndReceiveUserIdAndContactId(bean, applyUserId, receiveUserId, contactId);
    }

    /**
     * 根据ApplyUserIdAndReceiveUserIdAndContactId删除
     */
    @Override
    public Integer deleteUserContactApplyByApplyUserIdAndReceiveUserIdAndContactId(String applyUserId, String receiveUserId, String contactId) {
        return this.userContactApplyMapper.deleteByApplyUserIdAndReceiveUserIdAndContactId(applyUserId, receiveUserId, contactId);
    }

    /**
     * 处理好友申请
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dealWithApply(String userId, Integer applyId, Integer status) {
        UserContactApplyStatusEnum statusEnum = UserContactApplyStatusEnum.getByStatus(status);
        if (statusEnum == null || UserContactApplyStatusEnum.INIT.equals(statusEnum)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserContactApply applyInfo = userContactApplyMapper.selectByApplyId(applyId);
        if (applyInfo == null || !userId.equals(applyInfo.getReceiveUserId())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserContactApply updateInfo = new UserContactApply();
        updateInfo.setStatus(statusEnum.getStatus());
        updateInfo.setLastApplyTime(System.currentTimeMillis());

        UserContactApplyQuery query = new UserContactApplyQuery();
        query.setApplyId(applyId);
        query.setStatus(UserContactApplyStatusEnum.INIT.getStatus());

        Integer count = userContactApplyMapper.updateByParam(updateInfo, query);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        if (UserContactApplyStatusEnum.PASS.getStatus().equals(status)) {
            userContactService.addContact(applyInfo.getApplyUserId(), applyInfo.getReceiveUserId(), applyInfo.getContactId(), applyInfo.getContactType(), applyInfo.getApplyInfo());
            return;
        }

        //首次拉黑
        if (UserContactApplyStatusEnum.BLACKLIST.getStatus().equals(status)) {
            //1.更细关系
            Date curDate = new Date();
            UserContact userContact = new UserContact();
            userContact.setUserId(applyInfo.getApplyUserId());
            userContact.setContactId(applyInfo.getContactId());
            userContact.setContactType(applyInfo.getContactType());
            userContact.setStatus(UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus());
            userContact.setCreateTime(curDate);
            userContact.setUpdateTime(curDate);
            userContactMapper.insertOrUpdate(userContact);
        }
    }
}
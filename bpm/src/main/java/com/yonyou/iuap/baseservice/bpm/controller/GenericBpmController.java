package com.yonyou.iuap.baseservice.bpm.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.yonyou.iuap.base.web.BaseController;
import com.yonyou.iuap.baseservice.bpm.entity.BpmSimpleModel;
import com.yonyou.iuap.baseservice.bpm.service.GenericBpmService;
import com.yonyou.iuap.baseservice.bpm.utils.BpmExUtil;
import com.yonyou.iuap.bpm.service.JsonResultService;
import com.yonyou.iuap.bpm.web.IBPMBusinessProcessController;
import com.yonyou.iuap.mvc.type.JsonResponse;
import com.yonyou.iuap.persistence.vo.pub.BusinessException;
import net.sf.json.JSONNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import yonyou.bpm.rest.request.AssignInfo;
import yonyou.bpm.rest.request.Participant;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 说明：工作流基础Controller：提供单据增删改查，以及工作流提交、撤回、以及工作流流转回调方法
 * @author Aton
 * 2018年6月13日
 *
 * @update  将依赖sdk的rest接口转移到GenericBpmSdkController by Leon
 */
public  class GenericBpmController<T extends BpmSimpleModel> extends BaseController implements IBPMBusinessProcessController {

	private Logger logger= LoggerFactory.getLogger(this.getClass());

	@Autowired
	private JsonResultService jsonResultService;

	private GenericBpmService<T> service;

	public void setService(GenericBpmService<T> bpmService) {
		this.service = bpmService;
	}

	/**
	 * 说明：原始方法中可以提交多个单据，遍历循环启动多个流程实例
	 * 更新：现有实现，传入流程单据列表，service实现中也只提交第一条流程单据，产生一个流程实例
	 * @param list
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value = "/submit", method = RequestMethod.POST)
	@ResponseBody
	public Object submit(@RequestBody List<T> list, HttpServletRequest request, HttpServletResponse response) {
		String processDefineCode = request.getParameter("processDefineCode");
		if (processDefineCode==null){ throw new BusinessException("入参流程定义为空"); }
		try{
			Object result= service.submit(list,processDefineCode);
			return super.buildSuccess(result);
		}catch(Exception exp) {
			return this.buildGlobalError(exp.getMessage());
		}
	}

	/**
	 * 提交【支持抄送、指派】
	 */
	@RequestMapping(value = "/startBpm", method = RequestMethod.POST)
	@ResponseBody
	public Object startBpm(@RequestBody Map<String, Object> data, HttpServletRequest request, HttpServletResponse response) {
		try {
			Type superclassType = this.getClass().getGenericSuperclass();
			if (!ParameterizedType.class.isAssignableFrom(superclassType.getClass())) {
				return null;
			}
			Type[] t = ((ParameterizedType) superclassType).getActualTypeArguments();

			String processDefineCode = data.get("processDefineCode").toString();
			Object map = data.get("obj");
			String mj=  JSONObject.toJSONString(map);

			T entity = (T) JSON.parseObject(mj,t[0], Feature.IgnoreNotMatch);

			String aj=  JSONObject.toJSONString(data.get("assignInfo"));
			AssignInfo assignInfo = jsonResultService.toObject(aj, AssignInfo.class);
			entity.setProcessDefineCode(processDefineCode);


			List<Participant> copyUserParticipants=null;
			try {
				//抄送人
				copyUserParticipants = evalParticipant((List<Map>)data.get("copyusers"));
				logger.debug("抄送信息对象化：{}",JSONObject.toJSONString(copyUserParticipants));
			}catch (Exception e){
				logger.error("暂无指派抄送信息，可忽略。");
			}
			service.assignSubmitEntity(entity, processDefineCode, assignInfo, copyUserParticipants);
			return super.buildSuccess(entity);
		} catch (Exception e) {
			return super.buildGlobalError(e.getMessage());
		}

	}



	/**
	 *
	 * @param data
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/assignSubmit", method = RequestMethod.POST)
	@ResponseBody
	public Object assignSubmit(@RequestBody Map<String, Object> data,HttpServletRequest request) {
		try {
			Type superclassType = this.getClass().getGenericSuperclass();
			if (!ParameterizedType.class.isAssignableFrom(superclassType.getClass())) {
				return null;
			}
			Type[] t = ((ParameterizedType) superclassType).getActualTypeArguments();

			String processDefineCode = data.get("processDefineCode").toString();
			Object map = data.get("obj");
			String mj=  JSONObject.toJSONString(map);

			T entity = (T) JSON.parseObject(mj,t[0], Feature.IgnoreNotMatch);

			String aj=  JSONObject.toJSONString(data.get("assignInfo"));
			AssignInfo assignInfo = jsonResultService.toObject(aj, AssignInfo.class);
			entity.setProcessDefineCode(processDefineCode);

			List<Participant> copyUserParticipants=null;
			try {
				//抄送人
				copyUserParticipants = evalParticipant((List<Map>)data.get("copyusers"));
				logger.debug("抄送信息对象化：{}",JSONObject.toJSONString(copyUserParticipants));
			}catch (Exception e){
				logger.error("暂无指派抄送信息，可忽略。错误信息：{}",e.getMessage());
			}
			service.assignSubmitEntity(entity, processDefineCode, assignInfo, copyUserParticipants);
			return super.buildSuccess(entity);
		} catch (Exception e) {
			return super.buildGlobalError(e.getMessage());
		}
	}

	/**
	 * 撤回申请
	 */
	@RequestMapping(value = "/recall", method = RequestMethod.POST)
	@ResponseBody
	public Object recall(@RequestBody List<T> list, HttpServletRequest request, HttpServletResponse response) {
		Object unsubmitJson = service.batchRecall(list);
		return super.buildSuccess(unsubmitJson);
	}


	/**
	 * 回调:审批通过
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@ResponseBody
	public Object doApproveAction(@RequestBody Map<String, Object> params, HttpServletRequest request) throws Exception {
		Object node = params.get("historicProcessInstanceNode");
		if (node==null) throw new BusinessException("流程审批回调参数为空");
		Map hisProc = (Map)node;
		Object endTime = hisProc.get("endTime");
		String busiId = hisProc.get("businessKey").toString();
		T entity=service.findById(busiId);
		if (endTime != null && endTime != JSONNull.getInstance() && !"".equals(endTime)) {
			entity.setBpmState(BpmExUtil.BPM_STATE_FINISH);//已办结
		}else {
			entity.setBpmState(BpmExUtil.BPM_STATE_RUNNING);//审批中
		}
		T result = service.save(entity);
		return buildSuccess(result);
	}


	/***
	 * 流程终止回调默认实现
	 * @param params
	 * @return
	 * @throws Exception
	 */
	@ResponseBody
	public JsonResponse doTerminationAction(@RequestBody Map<String, Object> params) throws Exception {
		Object node = params.get("historicProcessInstanceNode");
		if (node==null) throw new BusinessException("流程终止回调参数为空");
		Map hisProc = (Map)node;
		String busiId = hisProc.get("businessKey").toString();
		T entity=service.findById(busiId);
		entity.setBpmState(BpmExUtil.BPM_STATE_ABEND);//异常终止
		T result = service.save(entity);
		return buildSuccess(result);
	}


	/**
	 * 回调：驳回到制单人
	 * @param params
	 * @return JsonResponse
	 * @throws Exception
	 */
	@ResponseBody
	public JsonResponse doRejectMarkerBillAction(@RequestBody Map<String, Object> params) throws Exception {
		String billId = String.valueOf(params.get("billId"));
		logger.debug("doRejectMarkerBillAction处理单据：{}",billId);
		service.doRejectMarkerBill(billId);
		T entity=service.findById(billId);
		entity.setBpmState(BpmExUtil.BPM_STATE_NOTSTART);//异常终止
        logger.debug("doRejectMarkerBillAction处理单据状态：{}",entity.getBpmState());
		T result = service.save(entity);
		return buildSuccess(result);
	}

	@Override
	public JsonResponse doReject(Map<String, Object> map) throws Exception {
		logger.debug("doReject");
		return buildSuccess();
	}

	@Override
	public JsonResponse doAddSign(Map<String, Object> map) throws Exception {
		logger.debug("doAddSign");
		return buildSuccess();
	}

	@Override
	public JsonResponse doDelegate(Map<String, Object> map) throws Exception {
		logger.debug("doDelegate");
		return buildSuccess();
	}

	@Override
	public JsonResponse doAssign(Map<String, Object> map) throws Exception {
		logger.debug("doAssign");
		return buildSuccess();
	}

	@Override
	public JsonResponse doWithdraw(Map<String, Object> map) throws Exception {
		logger.debug("doWithdraw");
		return buildSuccess();
	}

	@Override
	public JsonResponse doCompletedWithdraw(Map<String, Object> params) throws Exception {
		logger.debug("doCompletedWithdraw begin");
        Object node = params.get("historicProcessInstanceNode");
        if (node==null) throw new BusinessException("流程终止回调参数为空");
        Map hisProc = (Map)node;
        String busiId = hisProc.get("businessKey").toString();
        logger.debug(busiId);
        T entity=service.findById(busiId);
        entity.setBpmState(BpmExUtil.BPM_STATE_RUNNING);//流程中
        T result = service.save(entity);
        logger.debug(JSONObject.toJSONString(entity));
		logger.debug("doCompletedWithdraw end.");
        return buildSuccess(result);
	}

	@Override
	public JsonResponse doSuspend(Map<String, Object> map) throws Exception {
		logger.debug("doSuspend");
		return buildSuccess();
	}

	@Override
	public JsonResponse doActivate(Map<String, Object> map) throws Exception {
		logger.debug("doActivate");
		return buildSuccess();
	}

	/**
	 * 构造抄送人员participant列表
	 * @param copyusers
	 * @return
	 */
	private List<Participant> evalParticipant(List<Map> copyusers) {
		logger.debug("抄送集合数据：{}",JSONObject.toJSONString(copyusers));
		List<Participant> participants=new ArrayList<>();
		for(Map map:copyusers){
			Participant participant=new Participant();
			participant.setId(map.get("id").toString());
			participant.setType(map.get("type").toString());
			participants.add(participant);
		}
		logger.debug("抄送对象数据：{}",JSONObject.toJSONString(participants));
		return participants;
	}

}
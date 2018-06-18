package com.yonyou.iuap.baseservice.persistence.mybatis.ext.adapter.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.persistence.Column;
import org.apache.ibatis.mapping.SqlCommandType;

import com.yonyou.iuap.baseservice.persistence.mybatis.ext.adapter.SqlTemplate;
import com.yonyou.iuap.baseservice.persistence.mybatis.ext.exception.MapperException;
import com.yonyou.iuap.baseservice.persistence.mybatis.ext.support.Dialect;
import com.yonyou.iuap.baseservice.persistence.mybatis.ext.utils.EntityUtil;
import com.yonyou.iuap.baseservice.persistence.mybatis.ext.utils.FieldUtil;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;

/**
 * 说明：
 * @author Aton
 * 2018年6月19日
 */
public class MysqlInsertTemplate implements SqlTemplate{
	
	private Logger log = LoggerFactory.getLogger(MysqlInsertTemplate.class);

	@Override
	public Dialect getDialect() {
		return Dialect.mysql;
	}
	
	public SqlCommandType getSQLType() {
		return SqlCommandType.INSERT;
	}
	
	@Override
	public String parseSQL(Method method, Class<?> entityClazz) {
		StringBuilder columnSql = new StringBuilder();
		StringBuilder valuesSql = new StringBuilder();
		boolean isFirst = true;
		for(Field field : ReflectUtil.getFields(entityClazz)) {
			if(FieldUtil.insertable(field)) {
            	if(!isFirst) {
            		columnSql.append(", ");
            		valuesSql.append(", ");
            	}
            	this.build(field, columnSql, valuesSql);
               	isFirst = false;
			}
		}
		if(!isFirst) {
			return new StringBuilder("INSERT INTO ").append(EntityUtil.getTableName(entityClazz))
								.append(" (").append(columnSql).append(") VALUES (")
								.append(valuesSql).append(")").toString();
		}else {
			throw new MapperException();
		}
	}
	
	private void build(Field field, StringBuilder columnSql, StringBuilder valuesSql) {
        Column column = field.getAnnotation(Column.class);
        if (column==null || StrUtil.isEmpty(column.name())) {			//补充内容,比如驼峰规则
            columnSql.append(FieldUtil.getColumnName(field));
            valuesSql.append("#{").append(field.getName()).append("}");
        }else {
            columnSql.append(column.name());
            valuesSql.append("#{").append(field.getName()).append("}");
        }
	}

}
package net.nosegrind.apiframework


import java.beans.BeanInfo
import java.beans.PropertyDescriptor
import java.beans.Introspector
import java.lang.reflect.InvocationTargetException

//@GrailsCompileStatic
class ApiParams{

	ParamsDescriptor param
	
	private static final INSTANCE = new ApiParams()
	
	static getInstance(){ return INSTANCE }
	
	private ApiParams() {}

	
	def toObject(){
		Map<String, Object> result = new HashMap<String, Object>()
		BeanInfo info = Introspector.getBeanInfo(param.getClass())
		for (PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
			try{
	            Object propertyValue = descriptor.getReadMethod().invoke(param)
	            if (propertyValue != null) {
					if(!['metaClass','class','errors','values'].contains(descriptor.getName())){
						result.put(descriptor.getName(),propertyValue)
					}
	            }
			}catch (final IllegalArgumentException e){
				throw new Exception("[ApiParams :: toObject] : IllegalArgumentException - full stack trace follows:",e)
			}catch (final IllegalAccessException e){
				throw new Exception("[ApiParams :: toObject] : IllegalAccessException - full stack trace follows:",e)
			}catch (final InvocationTargetException e){
				throw new Exception("[ApiParams :: toObject] : InvocationTargetException - full stack trace follows:",e)
			}
		}
		
		return result
	}
	
	def setMockData(String data){
		this.param.mockData = data
		return this
	}
	
	def setDescription(String data){
		this.param.description = data
		return this
	}

	def setKey(String data){
		this.param.keyType = data
		return this
	}


	def hasParams(ParamsDescriptor[] values){
		this.param.values = values
		return this
	}

	def setReference(String data){
		this.param.idReferences = data
		return this
	}
	
	def setParam(String type,String name){
		this.param = new ParamsDescriptor(paramType:"${type}",name:"${name}")
		return this
	}
}

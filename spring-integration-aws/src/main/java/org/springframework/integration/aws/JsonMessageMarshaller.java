package org.springframework.integration.aws;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Marshaller implementation for JSON.
 * 
 * @author Sayantam Dey
 * 
 */
public class JsonMessageMarshaller implements MessageMarshaller {

	private static final String HEADER_VALUE_KEY = "value";
	private static final String HEADER_CLAZZ_KEY = "clazz";
	private static final String PAYLOAD_KEY = "payload";
	private static final String HEADERS_KEY = "headers";
	private static final String PROPERTIES_KEY = "properties";

	private ObjectMapper objectMapper;

	@Override
	public String serialize(Message<?> message)
			throws MessageMarshallerException {

		try {
			final Map<String, String> messageProperties = extractMessageProperties(new IntegrationMessageHeaderAccessor(message));
			final Map<String, String> headersMap = convertHeadersToMap(message
					.getHeaders());
			final Object payload = message.getPayload();

			return getObjectMapper().writeValueAsString(new Object() {

				@SuppressWarnings("unused")
				public Object getPayload() {
					return payload;
				}

				@SuppressWarnings("unused")
				public String getPayloadClazz() {
					return payload.getClass().getName();
				}

				@SuppressWarnings("unused")
				public Map<String, String> getHeaders() {
					return headersMap;
				}

				@SuppressWarnings("unused")
				public Map<String, String> getProperties() {
					return messageProperties;
				}
			});

		} catch (Exception e) {
			throw new MessageMarshallerException(e.getMessage(), e.getCause());
		}
	}

	private Map<String, String> extractMessageProperties(
	    IntegrationMessageHeaderAccessor messageHeaders) {

		Map<String, String> map = new HashMap<String, String>();
		if (messageHeaders.getCorrelationId() != null) {
			map.put(HeaderKeys.CORRELATION_ID, messageHeaders
					.getCorrelationId().toString());
		}
		if (messageHeaders.getExpirationDate() != null) {
			map.put(HeaderKeys.EXPIRATION_DATE, messageHeaders
					.getExpirationDate().toString());
		}
		if (messageHeaders.getPriority() != null) {
			map.put(HeaderKeys.PRIORITY, messageHeaders.getPriority()
					.toString());
		}
		if (messageHeaders.getSequenceNumber() != null) {
			map.put(HeaderKeys.SEQUENCE_NUMBER, messageHeaders
					.getSequenceNumber().toString());
		}
		if (messageHeaders.getSequenceSize() != null) {
			map.put(HeaderKeys.SEQUENCE_SIZE, messageHeaders.getSequenceSize()
					.toString());
		}
		return map;
	}

	private Map<String, String> convertHeadersToMap(
			MessageHeaders messageHeaders) throws JSONException, IOException {

		Map<String, String> map = new HashMap<String, String>();
		JSONObject headerObject = new JSONObject();
		for (Map.Entry<String, Object> element : messageHeaders.entrySet()) {
			String key = element.getKey();
			if (key.equals("id") || key.equals("timestamp")) {
				// always generated by the builder, no point storing them
				continue;
			}
			Object value = element.getValue();
			headerObject.put(HEADER_CLAZZ_KEY, value.getClass().getName());
			if (!String.class.isInstance(value)
					&& !Number.class.isInstance(value)) {
				headerObject.put(HEADER_VALUE_KEY, getObjectMapper()
						.writeValueAsString(value));
			} else {
				headerObject.put(HEADER_VALUE_KEY, value);
			}
			map.put(key, headerObject.toString());
		}
		return map;
	}

	@Override
	public Message<?> deserialize(String json)
			throws MessageMarshallerException {

		try {
			JSONObject jsonObject = null;
			Class<?> payloadClass = null;
			Object payload = null;
			JSONObject headers = null;
			JSONObject properties = null;

			try {
				jsonObject = new JSONObject(json);
			} catch (JSONException e) {
				// plain text
				payloadClass = String.class;
				payload = json;
			}
			if (payloadClass == null) {
				payloadClass = Class.forName(jsonObject
						.getString("payloadClazz"));

				String content = null;
				if (payloadClass.equals(String.class)) {
					payload = jsonObject.getString(PAYLOAD_KEY);

				} else if (payloadClass.isArray()) {
					content = jsonObject.getJSONArray(PAYLOAD_KEY).toString();

				} else if (Number.class.isAssignableFrom(payloadClass)) {
					payload = jsonObject.get(PAYLOAD_KEY);

				} else {
					JSONObject payloadJSONObject = jsonObject
							.getJSONObject(PAYLOAD_KEY);
					content = payloadJSONObject.toString();
				}

				if (content != null) {
					payload = getObjectMapper()
							.readValue(content, payloadClass);
				}

				properties = jsonObject.getJSONObject(PROPERTIES_KEY);
				headers = jsonObject.getJSONObject(HEADERS_KEY);
			}

			MessageBuilder<Object> builder = MessageBuilder
					.withPayload(payload);
			if (properties != null) {
				setProperties(builder, properties);
			}
			if (headers != null) {
				copyHeaders(builder, headers);
			}

			return builder.build();

		} catch (Exception e) {
			throw new MessageMarshallerException(e.getMessage(), e.getCause());
		}
	}

	private void setProperties(MessageBuilder<Object> builder,
			JSONObject properties) throws JSONException {

	     
		if (properties.has(HeaderKeys.CORRELATION_ID)) {
			builder.setHeader(IntegrationMessageHeaderAccessor.CORRELATION_ID, properties
					.getString(HeaderKeys.CORRELATION_ID));
		}
		if (properties.has(HeaderKeys.EXPIRATION_DATE)) {
		    builder.setHeader(IntegrationMessageHeaderAccessor.EXPIRATION_DATE, Long.valueOf(properties
					.getString(HeaderKeys.EXPIRATION_DATE)));
		}
		if (properties.has(HeaderKeys.PRIORITY)) {
		    builder.setHeader(IntegrationMessageHeaderAccessor.PRIORITY,  Integer.valueOf(properties
					.getString(HeaderKeys.PRIORITY)));
		}
		if (properties.has(HeaderKeys.SEQUENCE_NUMBER)) {
		    builder.setHeader(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER,Integer.valueOf(properties
					.getString(HeaderKeys.SEQUENCE_NUMBER)));
		}
		if (properties.has(HeaderKeys.SEQUENCE_SIZE)) {
		    builder.setHeader(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE,Integer.valueOf(properties
					.getString(HeaderKeys.SEQUENCE_SIZE)));
		}
	}

	private void copyHeaders(MessageBuilder<Object> builder, JSONObject headers)
			throws JSONException, ClassNotFoundException, IOException {

		Map<String, Object> map = new HashMap<String, Object>();
		String[] fieldNames = JSONObject.getNames(headers);
		if (fieldNames == null) {
			return;
		}
		for (String fieldName : fieldNames) {
			JSONObject headerObject = new JSONObject(
					headers.getString(fieldName));
			Class<?> clazz = Class.forName(headerObject
					.getString(HEADER_CLAZZ_KEY));
			if (String.class.equals(clazz)) {
				map.put(fieldName, headerObject.getString(HEADER_VALUE_KEY));
			} else if (Number.class.isAssignableFrom(clazz)) {
				map.put(fieldName, headerObject.get(HEADER_VALUE_KEY));
			} else {
				String source = headerObject.getString(HEADER_VALUE_KEY);
				map.put(fieldName, getObjectMapper().readValue(source, clazz));
			}
		}
		builder.copyHeaders(map);
	}

	/**
	 * Override this method to provide a custom ObjectMapper.
	 * 
	 * @return ObjectMapper
	 */
	protected ObjectMapper getObjectMapper() {

		if (this.objectMapper == null) {
			this.objectMapper = new ObjectMapper();
		}
		return this.objectMapper;
	}

	private abstract class HeaderKeys {
		public static final String CORRELATION_ID = "CorrelationId";
		private static final String EXPIRATION_DATE = "ExpirationDate";
		private static final String PRIORITY = "Priority";
		private static final String SEQUENCE_NUMBER = "SequenceNumber";
		private static final String SEQUENCE_SIZE = "SequenceSize";
	}

}

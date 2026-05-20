package org.openmrs.module.appointmentwebhook.fhir;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Minimal JSON writer — no dependencies, just StringBuilder.
 */
public class JsonWriter {
	
	private final StringBuilder sb;
	
	private boolean needsComma = false;
	
	private static final String FHIR_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
	
	public JsonWriter() {
		this.sb = new StringBuilder();
	}
	
	public JsonWriter objectStart() {
		comma();
		sb.append('{');
		needsComma = false;
		return this;
	}
	
	public JsonWriter objectEnd() {
		sb.append('}');
		needsComma = true;
		return this;
	}
	
	public JsonWriter arrayStart() {
		comma();
		sb.append('[');
		needsComma = false;
		return this;
	}
	
	public JsonWriter arrayEnd() {
		sb.append(']');
		needsComma = true;
		return this;
	}
	
	public JsonWriter key(String name) {
		comma();
		writeString(name);
		sb.append(':');
		needsComma = false;
		return this;
	}
	
	public JsonWriter value(String val) {
		if (val == null)
			return nullValue();
		comma();
		writeString(val);
		needsComma = true;
		return this;
	}
	
	public JsonWriter value(int val) {
		comma();
		sb.append(val);
		needsComma = true;
		return this;
	}
	
	public JsonWriter value(boolean val) {
		comma();
		sb.append(val);
		needsComma = true;
		return this;
	}
	
	public JsonWriter dateValue(Date date) {
		if (date == null)
			return nullValue();
		SimpleDateFormat sdf = new SimpleDateFormat(FHIR_DATE_FORMAT);
		sdf.setTimeZone(TimeZone.getDefault());
		return value(sdf.format(date));
	}
	
	public JsonWriter nullValue() {
		comma();
		sb.append("null");
		needsComma = true;
		return this;
	}
	
	@Override
	public String toString() {
		return sb.toString();
	}
	
	private void comma() {
		if (needsComma)
			sb.append(',');
	}
	
	private void writeString(String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\b':
					sb.append("\\b");
					break;
				case '\f':
					sb.append("\\f");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
			}
		}
		sb.append('"');
	}
}

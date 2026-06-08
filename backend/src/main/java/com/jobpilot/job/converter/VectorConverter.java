package com.jobpilot.job.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.lang.reflect.Method;

@Converter(autoApply = false)
public class VectorConverter implements AttributeConverter<float[], Object> {

    @Override
    public Object convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < attribute.length; i++) {
            sb.append(attribute[i]);
            if (i < attribute.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        String vectorString = sb.toString();

        try {
            // Use reflection to avoid compile-time dependency on PostgreSQL driver classes
            Class<?> pgObjectClass = Class.forName("org.postgresql.util.PGobject");
            Object pgObject = pgObjectClass.getDeclaredConstructor().newInstance();
            
            Method setTypeMethod = pgObjectClass.getMethod("setType", String.class);
            Method setValueMethod = pgObjectClass.getMethod("setValue", String.class);
            
            setTypeMethod.invoke(pgObject, "vector");
            setValueMethod.invoke(pgObject, vectorString);
            
            return pgObject;
        } catch (Exception e) {
            // Fallback to string if reflection fails (Postgres may still handle it if cast)
            return vectorString;
        }
    }

    @Override
    public float[] convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        
        String data = dbData.toString();
        if (data.isEmpty() || data.equals("null")) {
            return null;
        }
        
        try {
            // Format is [v1,v2,...]
            String s = data.substring(1, data.length() - 1);
            if (s.isEmpty()) return new float[0];
            
            String[] parts = s.split(",");
            float[] res = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                res[i] = Float.parseFloat(parts[i].trim());
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }
}

package org.update4j.exc;

import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

public class ExceptionUtils {

    public static List<Throwable> getThrowableList(Throwable throwable) {
        final List<Throwable> list = new ArrayList<>();
        while (throwable != null && !list.contains(throwable)) {
            list.add(throwable);
            throwable = throwable.getCause();
        }
        return list;
    }

    public static boolean contains(List<Throwable> throwableList, Class<? extends Throwable> exceptionClass) {
        for (Throwable throwable : throwableList) {
            if (throwable.getClass().equals(exceptionClass) || exceptionClass.isAssignableFrom(throwable.getClass())){
                return true;
            }
        }
        return false;
    }
}

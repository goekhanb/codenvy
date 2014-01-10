/*
 *
 * CODENVY CONFIDENTIAL
 * ________________
 *
 * [2012] - [2013] Codenvy, S.A.
 * All Rights Reserved.
 * NOTICE: All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any. The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */


package com.codenvy.analytics.services.view;


import com.codenvy.analytics.Utils;
import com.codenvy.analytics.datamodel.*;
import com.codenvy.analytics.metrics.InitialValueNotFoundException;
import com.codenvy.analytics.metrics.Metric;
import com.codenvy.analytics.metrics.MetricFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** @author <a href="mailto:abazko@codenvy.com">Anatoliy Bazko</a> */
public class MetricRow extends AbstractRow {

    private static final String NAME                 = "name";
    private static final String FORMAT               = "format";
    private static final String DEFAULT_FORMAT       = "%,.0f";
    private static final String DESCRIPTION          = "description";
    private static final String FIELDS               = "fields";
    private static final String HIDE_NEGATIVE_VALUES = "hide-negative-values";

    private final Metric   metric;
    private final String   format;
    private final String[] fields;
    private final boolean  hideNegativeValues;

    public MetricRow(Map<String, String> parameters) {
        super(parameters);

        metric = MetricFactory.getMetric(parameters.get(NAME));
        format = parameters.containsKey(FORMAT) ? parameters.get(FORMAT) : DEFAULT_FORMAT;
        fields = parameters.containsKey(FIELDS) ? parameters.get(FIELDS).split(",") : new String[0];
        hideNegativeValues = parameters.containsKey(HIDE_NEGATIVE_VALUES) &&
                             Boolean.parseBoolean(parameters.get(HIDE_NEGATIVE_VALUES));
    }

    @Override
    public List<List<ValueData>> getData(Map<String, String> initialContext, int columns) throws IOException {
        try {
            if (fields.length == 0) {
                return Arrays.asList(getSingleRow(initialContext, columns));
            } else {
                return getMultipleRows(initialContext, columns);
            }
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }

    private List<ValueData> getSingleRow(Map<String, String> initialContext, int columns) throws IOException,
                                                                                                 ParseException {
        List<ValueData> result = new ArrayList<>();

        boolean descriptionExists = parameters.containsKey(DESCRIPTION);
        if (descriptionExists) {
            result.add(new StringValueData(parameters.get(DESCRIPTION)));
        }

        for (int i = descriptionExists ? 1 : 0; i < getOverriddenColumnsCount(columns); i++) {
            try {
                formatAndAddSingleValue(getMetricValue(initialContext), result);
            } catch (InitialValueNotFoundException e) {
                result.add(StringValueData.DEFAULT);
            }

            initialContext = Utils.prevDateInterval(initialContext);
        }

        return result;
    }

    private List<List<ValueData>> getMultipleRows(Map<String, String> initialContext, int columns) throws
                                                                                                   IOException,
                                                                                                   ParseException {
        List<List<ValueData>> result = new ArrayList<>();

        for (int i = 0; i < getOverriddenColumnsCount(columns); i++) {
            formatAndAddMultipleValues(getMetricValue(initialContext), result);
            initialContext = Utils.prevDateInterval(initialContext);
        }

        return result;
    }

    private void formatAndAddSingleValue(ValueData valueData, List<ValueData> singleValue) throws IOException {
        Class<? extends ValueData> clazz = valueData.getClass();

        if (clazz == StringValueData.class) {
            singleValue.add(valueData);

        } else if (clazz == LongValueData.class) {
            double value = ((LongValueData)valueData).getAsDouble();

            ValueData formattedValue;
            if (value == 0 || (value < 0 && hideNegativeValues)) {
                formattedValue = StringValueData.DEFAULT;
            } else {
                formattedValue = new StringValueData(String.format(format, value));
            }

            singleValue.add(formattedValue);

        } else if (clazz == DoubleValueData.class) {
            double value = ((DoubleValueData)valueData).getAsDouble();

            ValueData formattedValue;
            if (value == 0 || Double.isInfinite(value) || Double.isNaN(value) || (value < 0 && hideNegativeValues)) {
                formattedValue = StringValueData.DEFAULT;
            } else {
                formattedValue = new StringValueData(String.format(format, value));
            }

            singleValue.add(formattedValue);
        } else {
            throw new IOException("Unsupported class " + clazz);
        }
    }

    private void formatAndAddMultipleValues(ValueData valueData, List<List<ValueData>> multipleValues) throws
                                                                                                       IOException {
        Class<? extends ValueData> clazz = valueData.getClass();

        if (clazz == MapValueData.class) {
            Map<String, ValueData> items = ((MapValueData)valueData).getAll();

            List<ValueData> singleValue = new ArrayList<>();
            for (String field : fields) {
                formatAndAddSingleValue(items.containsKey(field) ? items.get(field)
                                                                 : StringValueData.DEFAULT,
                                        singleValue);
            }

            multipleValues.add(singleValue);

        } else if (clazz == ListValueData.class) {
            for (ValueData item : ((ListValueData)valueData).getAll()) {
                formatAndAddMultipleValues(item, multipleValues);
            }
        } else {
            throw new IOException("Unsupported class " + clazz);
        }
    }

    protected ValueData getMetricValue(Map<String, String> context) throws IOException {
        return metric.getValue(context);
    }
}
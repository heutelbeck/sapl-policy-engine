/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.benchmark.report;

import java.awt.Color;
import java.awt.Paint;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;

import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.util.SortOrder;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;

public class BarChart {
    private final JFreeChart                        chart;
    private final DefaultStatisticalCategoryDataset dataset;

    public BarChart(String title, DefaultStatisticalCategoryDataset dataset, String valueAxisLabel,
            String labelFormat) {
        this.dataset = dataset;
        // format axis
        final var xAxis = new CategoryAxis();
        final var yAxis = new NumberAxis(valueAxisLabel);
        xAxis.setLowerMargin(0.015d);
        xAxis.setUpperMargin(0.015d);
        yAxis.setUpperMargin(0.15d);

        // define the plot
        final var renderer     = new BarRenderer();
        final var categoryPlot = new CategoryPlot(dataset, xAxis, yAxis, renderer);
        categoryPlot.setOrientation(PlotOrientation.HORIZONTAL);
        categoryPlot.setRowRenderingOrder(SortOrder.ASCENDING);
        categoryPlot.setColumnRenderingOrder(SortOrder.ASCENDING);

        chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, categoryPlot, true);
        new StandardChartTheme("JFree").apply(chart);

        // outline around the var
        renderer.setDrawBarOutline(true);
        renderer.setDefaultOutlinePaint(Color.darkGray);

        // add labels
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}",
                new DecimalFormat(labelFormat), new DecimalFormat(labelFormat)));
        renderer.setDefaultPositiveItemLabelPosition(
                new ItemLabelPosition(ItemLabelAnchor.OUTSIDE3, TextAnchor.CENTER_LEFT));

        // set bar colors
        categoryPlot.setBackgroundPaint(new Color(211, 211, 211));
        Paint[] colorPalette = new Paint[] { new Color(0, 172, 178, 200), new Color(18, 67, 109, 179),
                new Color(239, 96, 19, 152), new Color(85, 177, 69, 200), };
        for (int i = 0; i < dataset.getRowCount(); i++) {
            if (i < colorPalette.length) {
                renderer.setSeriesPaint(i, colorPalette[i]);
            }
        }
    }

    public void saveToPNGFile(File file) throws IOException {
        final var height = 100 + dataset.getColumnCount() * dataset.getRowCount() * 22;
        saveToPNGFile(file, 900, height);
    }

    public void saveToPNGFile(File file, int width, int height) throws IOException {
        final var fos = Files.newOutputStream(file.toPath());
        ChartUtils.writeScaledChartAsPNG(fos, chart, width, height, 3, 3);
        fos.close();
    }
}

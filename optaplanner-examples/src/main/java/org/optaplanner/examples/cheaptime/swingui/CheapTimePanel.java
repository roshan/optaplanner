/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.examples.cheaptime.swingui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.SwingConstants;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.HighLowRenderer;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.util.ShapeUtilities;
import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.examples.cheaptime.domain.CheapTimeSolution;
import org.optaplanner.examples.cheaptime.domain.Machine;
import org.optaplanner.examples.cheaptime.domain.PeriodPowerPrice;
import org.optaplanner.examples.cheaptime.domain.Task;
import org.optaplanner.examples.cheaptime.domain.TaskAssignment;
import org.optaplanner.examples.common.swingui.SolutionPanel;

public class CheapTimePanel extends SolutionPanel {

    private StableTaskAssignmentComparator stableTaskAssignmentComparator = new StableTaskAssignmentComparator();
    private GroupByMachineTaskAssignmentComparator groupByMachineTaskAssignmentComparator = new GroupByMachineTaskAssignmentComparator();

    public static final String LOGO_PATH = "/org/optaplanner/examples/cheaptime/swingui/cheapTimeLogo.png";

    private JCheckBox groupByMachineCheckBox;

    public CheapTimePanel() {
        setLayout(new BorderLayout());
        groupByMachineCheckBox = new JCheckBox("Group by assigned machine", false);
        groupByMachineCheckBox.setHorizontalAlignment(SwingConstants.RIGHT);
        groupByMachineCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updatePanel(solutionBusiness.getSolution());
                validate();
            }
        });
    }

    @Override
    public boolean isRefreshScreenDuringSolving() {
        return true;
    }

    private CheapTimeSolution getCheapTimeSolution() {
        return (CheapTimeSolution) solutionBusiness.getSolution();
    }

    public void resetPanel(Solution solution) {
        removeAll();
        add(groupByMachineCheckBox, BorderLayout.NORTH);
        ChartPanel chartPanel = new ChartPanel(createChart((CheapTimeSolution) solution));
        add(chartPanel, BorderLayout.CENTER);
    }

    private JFreeChart createChart(CheapTimeSolution solution) {
        NumberAxis rangeAxis = new NumberAxis("Period");
        rangeAxis.setRange(-0.5, solution.getGlobalPeriodRangeTo() + 0.5);
        XYPlot taskAssignmentPlot = createTaskAssignmentPlot(solution);
        XYPlot periodCostPlot = createPeriodCostPlot(solution);
        CombinedRangeXYPlot combinedPlot = new CombinedRangeXYPlot(rangeAxis);
        combinedPlot.add(taskAssignmentPlot, 5);
        combinedPlot.add(periodCostPlot, 1);

        combinedPlot.setOrientation(PlotOrientation.HORIZONTAL);
        return new JFreeChart("Cheap Power Time Scheduling", JFreeChart.DEFAULT_TITLE_FONT,
                combinedPlot, true);
    }

    private XYPlot createTaskAssignmentPlot(CheapTimeSolution solution) {
        OHLCSeriesCollection seriesCollection = new OHLCSeriesCollection();
        Map<Machine, OHLCSeries> machineSeriesMap = new LinkedHashMap<Machine, OHLCSeries>(
                solution.getMachineList().size());
        HighLowRenderer renderer = new HighLowRenderer();
        renderer.setTickLength(0.0);
        int seriesIndex = 0;
        OHLCSeries unassignedProjectSeries = new OHLCSeries("Unassigned");
        seriesCollection.addSeries(unassignedProjectSeries);
        machineSeriesMap.put(null, unassignedProjectSeries);
        renderer.setSeriesStroke(seriesIndex, new BasicStroke(3.0f));
        seriesIndex++;
        for (Machine machine : solution.getMachineList()) {
            OHLCSeries projectSeries = new OHLCSeries(machine.getLabel());
            seriesCollection.addSeries(projectSeries);
            machineSeriesMap.put(machine, projectSeries);
            renderer.setSeriesStroke(seriesIndex, new BasicStroke(3.0f));
            seriesIndex++;
        }
        List<TaskAssignment> taskAssignmentList = new ArrayList<TaskAssignment>(solution.getTaskAssignmentList());
        Collections.sort(taskAssignmentList,
                groupByMachineCheckBox.isSelected() ? groupByMachineTaskAssignmentComparator
                        : stableTaskAssignmentComparator);
        int pixelIndex = 0;
        for (TaskAssignment taskAssignment : taskAssignmentList) {
            Task task = taskAssignment.getTask();
            Integer startPeriod = taskAssignment.getStartPeriod();
            Integer endPeriod = taskAssignment.getEndPeriod();
            if (startPeriod == null) {
                startPeriod = task.getStartPeriodRangeFrom();
                endPeriod = startPeriod + task.getDuration();
            }
            OHLCSeries machineSeries = machineSeriesMap.get(taskAssignment.getMachine());
            machineSeries.add(new FixedMillisecond(pixelIndex), task.getStartPeriodRangeFrom(),
                    startPeriod, endPeriod, task.getStartPeriodRangeTo() + task.getDuration());
            pixelIndex++;
        }
        NumberAxis domainAxis = new NumberAxis("Task");
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        domainAxis.setRange(-0.5, taskAssignmentList.size() - 0.5);
        domainAxis.setInverted(true);
        return new XYPlot(seriesCollection, domainAxis, null, renderer);
    }

    private XYPlot createPeriodCostPlot(CheapTimeSolution solution) {
        XYSeries series = new XYSeries("Power price");
        for (PeriodPowerPrice periodPowerPrice : solution.getPeriodPowerPriceList()) {
            series.add((double) periodPowerPrice.getPowerPriceMicros() / 1000000.0, periodPowerPrice.getPeriod());
        }
        XYSeriesCollection seriesCollection = new XYSeriesCollection();
        seriesCollection.addSeries(series);
        XYItemRenderer renderer = new StandardXYItemRenderer(StandardXYItemRenderer.SHAPES);
        renderer.setSeriesShape(0, ShapeUtilities.createDiamond(2.0F));
        NumberAxis domainAxis = new NumberAxis("Power price");
        return new XYPlot(seriesCollection, domainAxis, null, renderer);
    }

    private static class StableTaskAssignmentComparator implements Comparator<TaskAssignment>, Serializable {

        @Override
        public int compare(TaskAssignment a, TaskAssignment b) {
            return new CompareToBuilder()
                    .append(a.getTask().getStartPeriodRangeFrom(), b.getTask().getStartPeriodRangeFrom())
                    .append(a.getTask().getStartPeriodRangeTo(), b.getTask().getStartPeriodRangeTo())
                    .append(a.getTask().getDuration(), b.getTask().getDuration())
                    .append(a.getId(), b.getId())
                    .toComparison();
        }

    }

    private static class GroupByMachineTaskAssignmentComparator implements Comparator<TaskAssignment>, Serializable {

        @Override
        public int compare(TaskAssignment a, TaskAssignment b) {
            Machine aMachine = a.getMachine();
            Machine bMachine = b.getMachine();
            return new CompareToBuilder()
                    .append(aMachine == null ? null : aMachine.getId(), bMachine == null ? null : bMachine.getId())
                    .append(a.getStartPeriod(), b.getStartPeriod())
                    .append(a.getTask().getDuration(), b.getTask().getDuration())
                    .append(a.getId(), b.getId())
                    .toComparison();
        }

    }

}

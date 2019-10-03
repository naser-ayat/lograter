/*
 * Copyright (C) 2019 Peter Paul Bakker, Stokpop Software Solutions
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
package nl.stokpop.lograter.reportcreator;

import nl.stokpop.lograter.GraphConfig;
import nl.stokpop.lograter.LogRater;
import nl.stokpop.lograter.LogRaterException;
import nl.stokpop.lograter.command.CommandMain;
import nl.stokpop.lograter.command.CommandPerformanceCenterResults;
import nl.stokpop.lograter.graphs.LogGraphCreator;
import nl.stokpop.lograter.processor.performancecenter.PerformanceCenterConfig;
import nl.stokpop.lograter.processor.performancecenter.PerformanceCenterDataBundle;
import nl.stokpop.lograter.processor.performancecenter.PerformanceCenterResultsData;
import nl.stokpop.lograter.processor.performancecenter.PerformanceCenterResultsReader;
import nl.stokpop.lograter.processor.performancecenter.PerformanceCenterResultsReaderAccessDb;
import nl.stokpop.lograter.processor.performancecenter.PerformanceCenterResultsReaderSqlite;
import nl.stokpop.lograter.report.text.PerformanceCenterTextReport;
import nl.stokpop.lograter.store.RequestCounterStorePair;
import nl.stokpop.lograter.util.time.DateUtils;
import nl.stokpop.lograter.util.time.TimePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Create reports for performance center results databases.
 */
public class PerformanceCenterResultsReportCreator implements ReportCreatorWithCommand<CommandPerformanceCenterResults> {
	
	private static final Logger log = LoggerFactory.getLogger(PerformanceCenterResultsReportCreator.class);

	@Override
	public void createReport(PrintStream outputStream, CommandMain cmdMain, CommandPerformanceCenterResults cmdPCResults) throws IOException {
		List<String> files = cmdPCResults.files;
		if (files.size() != 1) {
			throw new LogRaterException("Please supply one results database path (Results.mdb or Results.db) from performance center.");
		}

		String databasePath = files.get(0);
		File resultsFile = new File(databasePath);

		PerformanceCenterResultsReader reader;
		if (databasePath.endsWith(".mdb")) {
			reader = new PerformanceCenterResultsReaderAccessDb(resultsFile);
		}
		else if (databasePath.endsWith(".db")) {
			reader = new PerformanceCenterResultsReaderSqlite(resultsFile);
		}
		else {
			throw new LogRaterException("Expected a Results database name that ends with either .mdb (Access database) or .db (sqlite3 database)");
		}

		PerformanceCenterResultsData data = reader.readResultsData();

		PerformanceCenterConfig config = new PerformanceCenterConfig();
		config.setRunId(cmdMain.runId);
		config.setFailureAwareAnalysis(true);
		config.setFilterPeriod(DateUtils.createFilterPeriod(cmdMain.startTimeStr, cmdMain.endTimeStr));
		LogRater.populateBasicCounterLogSettings(cmdPCResults, config);
		config.setCounterStorage(cmdMain.storage);
		// used as name in the header of the report (and maybe in more places?)
		config.setCounterFields("transaction-name");
		//config.setMapperFile(cmdPCResults.mapperFile);
		//config.setClickPathShortCodeLength(cmdPCResults.clickPathShortCodeLength);
		config.setFileFeederFilterExcludes(cmdPCResults.fileFeederFilterExcludes);
		config.setFileFeederFilterIncludes(cmdPCResults.fileFeederFilterIncludes);
		config.setFailureAwareAnalysisIncludeFailuresInMetrics(false);

		PerformanceCenterDataBundle dataBundle = new PerformanceCenterDataBundle(config, data);
		PerformanceCenterTextReport report = new PerformanceCenterTextReport(dataBundle);
		TimePeriod analysisPeriod = dataBundle.getTotalRequestCounterStorePair().totalTimePeriod().createFilterTimePeriodIfFilterIsSet(config.getFilterPeriod());

		LogRater.writeReport(report, cmdMain.outputFilename, new File(cmdMain.reportDirectory), outputStream, analysisPeriod);

		GraphConfig graphConfig = new GraphConfig();
		graphConfig.setAggregateDurationInSeconds(cmdPCResults.aggregateDurationInSeconds);
		graphConfig.setGraphsHistoEnabled(cmdPCResults.graphsHisto);
		graphConfig.setGraphsHistoSimulatorEnabled(cmdPCResults.graphsHistoSimulator);
		graphConfig.setGraphsPercentileEnabled(cmdPCResults.graphsPercentile);
		graphConfig.setGraphsResponseTimesEnabled(cmdPCResults.graphsResponseTimes);
		graphConfig.setGraphsTpsEnabled(cmdPCResults.graphsTps);
		graphConfig.setGraphsHtmlEnabled(cmdPCResults.graphsHtml);
		graphConfig.setGraphWithTrueTPSEnabled(cmdPCResults.graphWithTrueTPS);
		graphConfig.setBaseUnit(cmdMain.baseUnit);

		if (graphConfig.isGraphRequested()) {
			LogGraphCreator logGraphCreator = new LogGraphCreator(graphConfig);

			List<RequestCounterStorePair> stores = new ArrayList<>();
			stores.add(data.getRequestCounterStorePair());
			File graphFile = logGraphCreator.createHtmlChartFile(new File(cmdMain.reportDirectory), "pc-log-rater-charts.html", stores, analysisPeriod);
			log.info("Check out graphs: {}", graphFile);
		}
	}
}
// Copyright (C) 2011 Splunk Inc.
//
// Splunk Inc. licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.splunk.shuttl.archiver.importexport.csv;

import static com.splunk.shuttl.archiver.LogFormatter.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.splunk.shuttl.archiver.LocalFileSystemPaths;
import com.splunk.shuttl.archiver.importexport.GetsBucketsExportFile;
import com.splunk.shuttl.archiver.importexport.ShellExecutor;
import com.splunk.shuttl.archiver.importexport.csv.splunk.SplunkExportTool;
import com.splunk.shuttl.archiver.model.Bucket;
import com.splunk.shuttl.archiver.model.LocalBucket;
import com.splunk.shuttl.archiver.util.UtilsList;

/**
 * Exports a bucket to .csv format.
 */
public class BucketToCsvFileExporter {

	private static final Logger logger = Logger
			.getLogger(BucketToCsvFileExporter.class);

	private final SplunkExportTool exportTool;
	private final GetsBucketsExportFile getsBucketsExportFile;
	private final ShellExecutor shellExecutor;

	public BucketToCsvFileExporter(SplunkExportTool exportTool,
			GetsBucketsExportFile getsBucketsExportFile, ShellExecutor shellExecutor) {
		this.exportTool = exportTool;
		this.getsBucketsExportFile = getsBucketsExportFile;
		this.shellExecutor = shellExecutor;
	}

	/**
	 * @return Csv file of the {@link Bucket}
	 */
	public File exportBucketToCsv(LocalBucket bucket) {
		File csvFile = getsBucketsExportFile.getCsvFile(bucket);
		List<String> command = constructCommand(bucket, csvFile);
		Map<String, String> env = exportTool.getEnvironment();
		int exit = shellExecutor.executeCommand(env, command);
		throwCsvExceptionIfExportFailed(csvFile, exit, command);
		return csvFile;
	}

	private List<String> constructCommand(LocalBucket bucket, File csvFile) {
		List<String> executableCommand = exportTool.getExecutableCommand();
		List<String> arguments = Arrays.asList(new String[] {
				bucket.getDirectory().getAbsolutePath(), csvFile.getAbsolutePath(),
				"-csv" });
		return UtilsList.join(executableCommand, arguments);
	}

	private void throwCsvExceptionIfExportFailed(File csvFile, int exit,
			List<String> command) {
		if (exit != 0) {
			logger.debug(did("Exported a bucket to Csv",
					"Got a non zero exit code from export tool",
					"Zero exit code from export tool.", "exit_code", exit, "csv_file",
					csvFile, "command", command));
			throw new CsvExportFailedException("Exporttool exited with"
					+ " non zero exit status: " + exit
					+ ". Ran exporttool with command: " + command);
		} else if (!csvFile.exists()) {
			logger.debug(did("Exported a bucket to Csv",
					"Csv file didn't exist after the export", "The csv file to exist",
					"csv_file", csvFile));
			throw new CsvExportFailedException("Csv file didn't exist after "
					+ "the export. Something went wrong.");
		}
	}

	/**
	 * @return a CsvExporter
	 */
	public static BucketToCsvFileExporter create(
			LocalFileSystemPaths localFileSystemPaths) {
		return new BucketToCsvFileExporter(new SplunkExportTool(),
				new GetsBucketsExportFile(localFileSystemPaths),
				ShellExecutor.getInstance());
	}

}

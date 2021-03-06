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
package com.splunk.shuttl.archiver.usecases;

import static com.splunk.shuttl.testutil.TUtilsFile.*;
import static java.util.Arrays.*;
import static org.testng.Assert.*;

import java.util.HashMap;
import java.util.List;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.splunk.shuttl.archiver.LocalFileSystemPaths;
import com.splunk.shuttl.archiver.archive.ArchiveConfiguration;
import com.splunk.shuttl.archiver.archive.BucketArchiver;
import com.splunk.shuttl.archiver.archive.BucketFormat;
import com.splunk.shuttl.archiver.archive.BucketShuttlerFactory;
import com.splunk.shuttl.archiver.filesystem.ArchiveFileSystem;
import com.splunk.shuttl.archiver.filesystem.ArchiveFileSystemFactory;
import com.splunk.shuttl.archiver.filesystem.glacier.FakeArchiveTransferManager;
import com.splunk.shuttl.archiver.filesystem.glacier.GlacierArchiveFileSystem;
import com.splunk.shuttl.archiver.filesystem.glacier.GlacierArchiveFileSystemFactory;
import com.splunk.shuttl.archiver.filesystem.glacier.GlacierClient;
import com.splunk.shuttl.archiver.model.IllegalIndexException;
import com.splunk.shuttl.archiver.model.LocalBucket;
import com.splunk.shuttl.archiver.thaw.BucketThawer;
import com.splunk.shuttl.archiver.thaw.BucketThawerFactory;
import com.splunk.shuttl.archiver.thaw.SplunkIndexesLayer;
import com.splunk.shuttl.archiver.usecases.util.FakeSplunkIndexesLayer;
import com.splunk.shuttl.testutil.TUtilsBucket;
import com.splunk.shuttl.testutil.TUtilsEnvironment;
import com.splunk.shuttl.testutil.TUtilsFunctional;
import com.splunk.shuttl.testutil.TUtilsTestNG;

@Test(groups = { "end-to-end" })
public class GlacierRoundtripFunctionalTest {

	private BucketArchiver bucketArchiver;
	private BucketThawer bucketThawer;

	/**
	 * Exists to hide the "localArchiveFileSystem" from the other construction
	 * logic. It serves as the metadata store for glacier.
	 */
	private GlacierArchiveFileSystem getGlacierArchiveFileSystem(
			GlacierClient client, LocalFileSystemPaths localPaths,
			ArchiveConfiguration csvConfig) {
		ArchiveFileSystem metaStore = ArchiveFileSystemFactory
				.getWithConfiguration(csvConfig);
		return GlacierArchiveFileSystemFactory.create(localPaths, client,
				metaStore, csvConfig);
	}

	@Parameters(value = { "splunk.home" })
	public void _givenCsvConfig_archivesThenThawsBucketThatIsEqualToTheOriginalBucket(
			final String splunkHome) throws IllegalIndexException {
		ArchiveConfiguration csvConfig = TUtilsFunctional
				.getLocalCsvArchiveConfigration();
		assertArchiveThenThawedBucketIsImportedFromConfigsFormat(csvConfig,
				splunkHome);
	}

	@Parameters(value = { "splunk.home" })
	public void _givenTgzConfig_archivesThenThawsBucketThatIsEqualToTheOriginalBucket(
			final String splunkHome) throws IllegalIndexException {
		ArchiveConfiguration tgzConfig = TUtilsFunctional
				.getLocalConfigurationThatArchivesFormats(asList(BucketFormat.SPLUNK_BUCKET_TGZ));
		assertArchiveThenThawedBucketIsImportedFromConfigsFormat(tgzConfig,
				splunkHome);
	}

	private void assertArchiveThenThawedBucketIsImportedFromConfigsFormat(
			ArchiveConfiguration config, final String splunkHome)
			throws IllegalIndexException {

		setup(config);
		final LocalBucket realBucketThatGotThawed = archiveAndThawBucket(splunkHome);
		assertBucketWasSuccessfullyThawedAndImported(realBucketThatGotThawed);
	}

	private void setup(ArchiveConfiguration config) throws IllegalIndexException {
		GlacierClient client = new GlacierClient(new FakeArchiveTransferManager(
				createDirectory()), "vault", new HashMap<String, String>());
		LocalFileSystemPaths localFileSystemPaths = new LocalFileSystemPaths(
				createDirectory());

		GlacierArchiveFileSystem glacierArchive = getGlacierArchiveFileSystem(
				client, localFileSystemPaths, config);

		bucketArchiver = BucketShuttlerFactory
				.createWithConfFileSystemAndLocalPaths(config, glacierArchive,
						localFileSystemPaths);

		SplunkIndexesLayer splunkIndexesLayer = new FakeSplunkIndexesLayer(
				createDirectory());

		bucketThawer = BucketThawerFactory.create(config, splunkIndexesLayer,
				localFileSystemPaths, glacierArchive);
	}

	private LocalBucket archiveAndThawBucket(final String splunkHome) {
		final LocalBucket realBucket = TUtilsBucket.createRealBucket();
		TUtilsFunctional.archiveBucket(realBucket, bucketArchiver, splunkHome);

		TUtilsEnvironment.runInCleanEnvironment(new Runnable() {

			@Override
			public void run() {
				TUtilsEnvironment.setEnvironmentVariable("SPLUNK_HOME", splunkHome);
				bucketThawer.thawBuckets(realBucket.getIndex(),
						realBucket.getEarliest(), realBucket.getLatest());
			}
		});
		return realBucket;
	}

	private void assertBucketWasSuccessfullyThawedAndImported(
			final LocalBucket realBucketThatGotThawed) {
		assertTrue(bucketThawer.getFailedBuckets().isEmpty());
		assertTrue(bucketThawer.getSkippedBuckets().isEmpty());

		List<LocalBucket> thawedBuckets = bucketThawer.getThawedBuckets();
		assertEquals(1, thawedBuckets.size());

		LocalBucket thawedBucket = thawedBuckets.get(0);
		TUtilsTestNG.assertBucketsGotSameIndexFormatAndName(
				realBucketThatGotThawed, thawedBucket);
		assertThawedBucketHasMoreThanACsvFile(thawedBucket);
	}

	private void assertThawedBucketHasMoreThanACsvFile(LocalBucket bucket) {
		int possibleHiddenFiles = 4;
		int filesInBucketDir = bucket.getDirectory().listFiles().length;
		assertTrue(filesInBucketDir > 1 + possibleHiddenFiles);
	}
}

/*
 TagRecommender:
 A framework to implement and evaluate algorithms for the recommendation
 of tags.
 Copyright (C) 2013 Dominik Kowald
 
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.
 
 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package engine;

import file.BookmarkReader;
import itemrecommendations.CFResourceCalculator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import common.Bookmark;
import common.DoubleMapComparator;
import common.Features;
import common.Similarity;

// TODO: cache values
public class CFUserRecommenderEngine implements EngineInterface {

	private BookmarkReader reader = null;
	private CFResourceCalculator calculator = null;
	private CFResourceCalculator tagCalculator = null;
	private final Map<Integer, Double> topUsers;

	public CFUserRecommenderEngine() {
		this.topUsers = new LinkedHashMap<Integer, Double>();		
		this.reader = new BookmarkReader(0, false);
	}
	
	public void loadFile(String filename) throws Exception {
		BookmarkReader reader = new BookmarkReader(0, false);
		reader.readFile(filename);
		Collections.sort(reader.getBookmarks());

		CFResourceCalculator calculator = new CFResourceCalculator(reader, reader.getBookmarks().size(), false, true, false, 5, Similarity.COSINE, Features.ENTITIES);
		CFResourceCalculator tagCalculator = new CFResourceCalculator(reader, reader.getBookmarks().size(), false, true, false, 5, Similarity.COSINE, Features.TAGS);
		
		Map<Integer, Double> topUsers = EngineUtils.calcTopEntities(reader, EntityType.USER);
		resetStructure(reader, calculator, tagCalculator, topUsers);
	}

	public synchronized Map<String, Double> getEntitiesWithLikelihood(String user, String resource, List<String> topics, Integer count,
			Boolean filterOwnEntities, Algorithm algorithm) { 
		
		if (count == null || count.doubleValue() < 1) {
			count = 10;
		}
		
		Map<Integer, Double> userIDs = new LinkedHashMap<>();
		Map<String, Double> userMap = new LinkedHashMap<>();
		if (this.reader == null || this.calculator == null) {
			System.out.println("No data has been loaded");
			return userMap;
		}
		int userID = -1;
		if (user != null) {
			userID = this.reader.getUsers().indexOf(user);
		}

		// first call CF if wished
		if (algorithm == null || algorithm != Algorithm.USERMP) {
			if (algorithm == Algorithm.USERTAGCF) {
				userIDs = this.tagCalculator.getRankedResourcesList(userID, false, false, false, filterOwnEntities.booleanValue(), true); // not sorted!
			} else {
				userIDs = this.calculator.getRankedResourcesList(userID, false, false, false, filterOwnEntities.booleanValue(), true); // not sorted!
			}
		}
		// then call MP if necessary
		if (userIDs.size() < count) {
			for (Map.Entry<Integer, Double> t : this.topUsers.entrySet()) {
				if (userIDs.size() < count) {
					// add MP users if they are not already in the recommeded list
					if (!userIDs.containsKey(t.getKey())) {
						userIDs.put(t.getKey(), t.getValue());
					}
				} else {
					break;
				}
			}
		}

		// sort
		Map<Integer, Double> sortedResultMap = new TreeMap<Integer, Double>(new DoubleMapComparator(userIDs));
		sortedResultMap.putAll(userIDs);
		
		// last map IDs back to strings
		for (Map.Entry<Integer, Double> tEntry : sortedResultMap.entrySet()) {
			if (userMap.size() < count) {
				userMap.put(this.reader.getUsers().get(tEntry.getKey()), tEntry.getValue());
			} else {
				break;
			}
		}
		
		return userMap;
	}

	public synchronized void resetStructure(BookmarkReader reader, CFResourceCalculator calculator, CFResourceCalculator tagCalculator, Map<Integer, Double> topUsers) {
		this.reader = reader;
		this.calculator = calculator;
		this.tagCalculator = tagCalculator;
		
		this.topUsers.clear();
		this.topUsers.putAll(topUsers);
	}
}

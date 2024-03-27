/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatamongoreactivedemo.config;

import org.bson.types.ObjectId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.sapl.springdatamongoreactivedemo.repository.Role;
import io.sapl.springdatamongoreactivedemo.repository.User;
import io.sapl.springdatamongoreactivedemo.repository.UserRepository;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

@Component
@AllArgsConstructor
public class DataLoader implements ApplicationRunner {

	private final UserRepository userRepository;

	@Override
	public void run(ApplicationArguments args) {

		userRepository.deleteAll().thenMany(getTestData().flatMap(userRepository::save)).subscribe();
	}

	Flux<User> getTestData() {
		return Flux.just(new User(new ObjectId("64de3bd9fbf82799677ed333"), "Alice", "Wonder", 30, Role.ADMIN, true),
				new User(new ObjectId("64de2fb8375aabd24878daa4"), "Malinda", "Perrot", 53, Role.ADMIN, true),
				new User(new ObjectId("64de3bd9fbf82799677ed336"), "Emerson", "Rowat", 82, Role.USER, false),
				new User(new ObjectId("64de3bd9fbf82799677ed337"), "Yul", "Barukh", 79, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed338"), "Terrel", "Woodings", 96, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed339"), "Martino", "Bartolijn", 33, Role.USER, false),
				new User(new ObjectId("64de3bd9fbf82799677ed33a"), "Konstantine", "Hampton", 96, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed33b"), "Cathleen", "Simms", 25, Role.ADMIN, false),
				new User(new ObjectId("64de3bd9fbf82799677ed33c"), "Adolphe", "Streeton", 46, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed33d"), "Alessandro", "Tomaskov", 64, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed33e"), "Hobie", "Maddinon", 32, Role.USER, false),
				new User(new ObjectId("64de3bd9fbf82799677ed33f"), "Franni", "Mingey", 57, Role.ADMIN, false),
				new User(new ObjectId("64de3bd9fbf82799677ed340"), "Giraldo", "Scade", 83, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed341"), "Pooh", "Cocks", 19, Role.ADMIN, true),
				new User(new ObjectId("64de3bd9fbf82799677ed342"), "Mario", "Albinson", 54, Role.USER, false),
				new User(new ObjectId("64de3bd9fbf82799677ed343"), "Olav", "Hoopper", 32, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed344"), "Tuckie", "Morfell", 35, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed345"), "Sylas", "Bickerstasse", 66, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed346"), "Kacey", "Angell", 94, Role.USER, false),
				new User(new ObjectId("64de3bd9fbf82799677ed347"), "Dame", "Negri", 67, Role.USER, true),
				new User(new ObjectId("64de3bd9fbf82799677ed348"), "Perren", "Durtnall", 75, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09cccf4"), "Katleen", "Capstaff", 82, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09cccf5"), "Candis", "Souza", 90, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09cccf6"), "Mac", "Deetlof", 55, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09cccf7"), "Tabby", "Skittreal", 93, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09cccf8"), "Adriano", "Tennet", 60, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09cccf9"), "Cameron", "Garnham", 39, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09cccfa"), "Jeri", "Toppin", 79, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09cccfb"), "Josselyn", "Tongs", 34, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09cccfc"), "Reynolds", "Buesnel", 55, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09cccfd"), "Hedwig", "Berrill", 66, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09cccfe"), "Karylin", "Schule", 82, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09cccff"), "Thaddeus", "Machin", 50, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd00"), "Rhody", "Vasilic", 72, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd01"), "Josy", "Skein", 58, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd02"), "Rachael", "Baukham", 57, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd03"), "Claudianus", "Courtois", 98, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd04"), "Imelda", "Gilkes", 65, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd05"), "Marcelia", "Elmer", 24, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd06"), "Lib", "Peschka", 89, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd07"), "Winna", "Shellshear", 46, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd08"), "Patty", "O Mahoney", 52, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd09"), "Jerrylee", "Lukas", 76, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd0a"), "Fielding", "MacGibbon", 53, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd0b"), "Tuckie", "Hugett", 29, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd0c"), "Penrod", "Munehay", 70, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd0d"), "Lexine", "Blakden", 92, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd0e"), "Petra", "Shackleford", 97, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd0f"), "Glenn", "Stennes", 45, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd10"), "Morry", "Wolfer", 23, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd11"), "Dianemarie", "Howgill", 69, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd12"), "Darline", "Hinsche", 39, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd13"), "Lou", "Kiossel", 62, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd14"), "Titus", "Gillbard", 38, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd15"), "Henderson", "Enticknap", 94, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd16"), "Leonie", "Miranda", 65, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd17"), "Amber", "Pink", 50, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd18"), "Forrest", "Izzett", 62, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd19"), "Lenette", "Fuster", 42, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd1a"), "Hazel", "Alston", 77, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd1b"), "Aldrich", "Maymond", 24, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd1c"), "Harmon", "Foulis", 30, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd1d"), "Mandy", "Fain", 66, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd1e"), "Sonnie", "Dilston", 32, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd1f"), "Wynne", "MacDearmaid", 73, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd20"), "Hyatt", "Cron", 43, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd21"), "Theodosia", "Zorzenoni", 34, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd22"), "Carleton", "Keyson", 79, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd23"), "Byran", "Dumbare", 22, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd24"), "Deina", "Watting", 92, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd25"), "Thacher", "Folca", 18, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd26"), "Gayle", "Orneles", 82, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd27"), "Ernestine", "Hatch", 82, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd28"), "Jonie", "Delle", 95, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd29"), "Lin", "Burleigh", 72, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd2a"), "Olva", "Ridding", 85, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd2b"), "Gray", "Ashall", 39, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd2c"), "Lorant", "Busch", 87, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd2d"), "Pryce", "Mosedill", 46, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd2e"), "Dori", "Norcutt", 37, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd2f"), "Amy", "Gurnett", 99, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd30"), "Lauraine", "Doogood", 54, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd31"), "Casper", "Upstell", 85, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd32"), "Lynnett", "Malloch", 62, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd33"), "Edi", "Giacopelo", 94, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd34"), "Bryanty", "Arnaud", 84, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd35"), "Nolana", "Masdon", 72, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd36"), "Jodi", "Corah", 58, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd37"), "Jazmin", "Sheehan", 45, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd38"), "Brandie", "Bushrod", 42, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd39"), "Darwin", "Atwill", 28, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd3a"), "Cicely", "Dearsley", 70, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd3b"), "Redford", "Palphreyman", 90, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd3c"), "Jany", "Lambourne", 92, Role.ADMIN, false),
				new User(new ObjectId("64de594d36d30786c09ccd3d"), "Tod", "Siddaley", 69, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd3e"), "Delinda", "Jerzykiewicz", 82, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd3f"), "Hobart", "Strand", 54, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd40"), "Erastus", "Spoure", 49, Role.USER, true),
				new User(new ObjectId("64de594d36d30786c09ccd41"), "Eydie", "Orys", 64, Role.ADMIN, true),
				new User(new ObjectId("64de594d36d30786c09ccd42"), "Bastian", "Dearden", 32, Role.USER, false),
				new User(new ObjectId("64de594d36d30786c09ccd43"), "Cassondra", "Colbridge", 73, Role.ADMIN, true));
	}
}

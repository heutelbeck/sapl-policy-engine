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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;

/**
 * Permission and access control functions for policy enforcement. Provides
 * semantic operations for checking and
 * manipulating permission bitmasks, with specialized support for POSIX/Unix
 * file permissions.
 * <p/>
 * Functions accept arrays of permission masks and combine them automatically,
 * eliminating the need for verbose manual
 * bitwise operations in policies.
 */
@UtilityClass
@FunctionLibrary(name = PermissionsFunctionLibrary.NAME, description = PermissionsFunctionLibrary.DESCRIPTION)
public class PermissionsFunctionLibrary {

    public static final String NAME        = "permissions";
    public static final String DESCRIPTION = "Permission and access control functions for checking and manipulating permission bitmasks, with specialized support for POSIX/Unix permissions.";

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final Value POSIX_READ    = Value.of(0x4L);
    private static final Value POSIX_WRITE   = Value.of(0x2L);
    private static final Value POSIX_EXECUTE = Value.of(0x1L);
    private static final Value POSIX_ALL     = Value.of(0x7L);
    private static final Value POSIX_NONE    = Value.of(0x0L);
    private static final Value POSIX_RW      = Value.of(0x6L);
    private static final Value POSIX_RX      = Value.of(0x5L);
    private static final Value POSIX_WX      = Value.of(0x3L);

    // POSIX file mode for owner: rwx, group: r-x, others: r-x (755)
    private static final Value POSIX_MODE_755 = Value.of(java.lang.Long.parseLong("755", 8));
    // POSIX file mode for owner: rw-, group: r--, others: r-- (644)
    private static final Value POSIX_MODE_644 = Value.of(java.lang.Long.parseLong("644", 8));
    // POSIX file mode for owner: rwx, group: rwx, others: rwx (777)
    private static final Value POSIX_MODE_777 = Value.of(java.lang.Long.parseLong("777", 8));
    // POSIX file mode for owner: rw-, group: ---, others: --- (600)
    private static final Value POSIX_MODE_600 = Value.of(java.lang.Long.parseLong("600", 8));
    // POSIX file mode for owner: rw-, group: rw-, others: rw- (666)
    private static final Value POSIX_MODE_666 = Value.of(java.lang.Long.parseLong("666", 8));

    private static final Value NONE = Value.of(0x0L);
    private static final Value ALL  = Value.of(-1L);

    private static final String ERROR_BIT_POSITION_INVALID       = "Bit position must be between 0 and 63.";
    private static final String ERROR_CANNOT_COMBINE_EMPTY_ARRAY = "Cannot combine empty array with AND.";
    private static final String ERROR_GROUP_PERMISSIONS_INVALID  = "Group permissions must be between 0 and 7.";
    private static final String ERROR_MASKS_MUST_BE_INTEGERS     = "All permission masks must be integers.";
    private static final String ERROR_MASKS_MUST_BE_NUMBERS      = "All permission masks must be numbers.";
    private static final String ERROR_OTHER_PERMISSIONS_INVALID  = "Other permissions must be between 0 and 7.";
    private static final String ERROR_OWNER_PERMISSIONS_INVALID  = "Owner permissions must be between 0 and 7.";

    /* Core Permission Semantics */

    @Function(docs = """
            ```hasAll(LONG value, ARRAY masks)```: Checks if all specified permission bits are set.

            Combines multiple permission masks using OR, then verifies that all resulting bits
            are present in the value. Useful for requiring multiple permissions simultaneously.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var CREATE = permissions.bit(0);
              var UPDATE = permissions.bit(1);
              var DELETE = permissions.bit(2);
              permissions.hasAll(subject.permissions, [CREATE, UPDATE, DELETE]);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value hasAll(NumberValue value, ArrayValue masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask instanceof ErrorValue) {
            return combinedMask;
        }

        val valueNumber = value.value().longValue();
        val maskNumber  = ((NumberValue) combinedMask).value().longValue();
        return Value.of((valueNumber & maskNumber) == maskNumber);
    }

    @Function(docs = """
            ```hasAny(LONG value, ARRAY masks)```: Checks if at least one of the specified permission bits is set.

            Combines multiple permission masks using OR, then verifies that at least one resulting
            bit is present in the value. Useful for allowing access if any permission matches.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var ADMIN = permissions.bit(0);
              var MODERATOR = permissions.bit(1);
              permissions.hasAny(subject.roles, [ADMIN, MODERATOR]);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value hasAny(NumberValue value, ArrayValue masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask instanceof ErrorValue) {
            return combinedMask;
        }

        val valueNumber = value.value().longValue();
        val maskNumber  = ((NumberValue) combinedMask).value().longValue();
        return Value.of((valueNumber & maskNumber) != 0);
    }

    @Function(docs = """
            ```hasNone(LONG value, ARRAY masks)```: Checks if none of the specified permission bits are set.

            Combines multiple permission masks using OR, then verifies that no resulting bits
            are present in the value. Useful for ensuring forbidden permissions are not granted.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var SUSPENDED = permissions.bit(0);
              var BANNED = permissions.bit(1);
              permissions.hasNone(subject.flags, [SUSPENDED, BANNED]);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value hasNone(NumberValue value, ArrayValue masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask instanceof ErrorValue) {
            return combinedMask;
        }

        val valueNumber = value.value().longValue();
        val maskNumber  = ((NumberValue) combinedMask).value().longValue();
        return Value.of((valueNumber & maskNumber) == 0);
    }

    @Function(docs = """
            ```hasExact(LONG value, LONG mask)```: Checks if the value has exactly the specified permission bits and no others.

            Verifies that the value equals the mask exactly. All specified bits must be set,
            and no additional bits may be set.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var READ_ONLY = permissions.bit(0);
              permissions.hasExact(subject.permissions, READ_ONLY);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value hasExact(NumberValue value, NumberValue mask) {
        return Value.of(value.value().longValue() == mask.value().longValue());
    }

    @Function(docs = """
            ```hasOnly(LONG value, ARRAY masks)```: Checks if the value has only the specified permission bits and no others.

            Combines multiple permission masks using OR, then verifies that the value contains
            only these bits and no additional bits. Unlike hasExact, not all specified bits
            need to be set, but no other bits may be set.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var READ = permissions.bit(0);
              var WRITE = permissions.bit(1);
              permissions.hasOnly(subject.permissions, [READ, WRITE]);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value hasOnly(NumberValue value, ArrayValue masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask instanceof ErrorValue) {
            return combinedMask;
        }

        val valueNumber = value.value().longValue();
        val maskNumber  = ((NumberValue) combinedMask).value().longValue();
        return Value.of((valueNumber & ~maskNumber) == 0);
    }

    @Function(docs = """
            ```combine(ARRAY masks)```: Combines multiple permission masks using bitwise OR.

            Returns a single mask with all bits from the input masks set. This is the union
            of all permission bits.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var CREATE = permissions.bit(0);
              var READ = permissions.bit(1);
              var UPDATE = permissions.bit(2);
              var fullAccess = permissions.combine([CREATE, READ, UPDATE]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value combine(ArrayValue masks) {
        return combineWithOr(masks);
    }

    @Function(docs = """
            ```combineAll(ARRAY masks)```: Combines multiple permission masks using bitwise AND.

            Returns a single mask with only the bits that are set in all input masks.
            This is the intersection of all permission bits.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var mask1 = numeral.fromHex("0x07");
              var mask2 = numeral.fromHex("0x03");
              var common = permissions.combineAll([mask1, mask2]);  // Result: 0x03
            ```
            """, schema = RETURNS_NUMBER)
    public static Value combineAll(ArrayValue masks) {
        return combineWithAnd(masks);
    }

    @Function(docs = """
            ```isSubsetOf(LONG permissions, LONG superset)```: Checks if permissions is a subset of superset.

            Returns true if all bits set in permissions are also set in superset. The superset
            may have additional bits set.

            **Examples:**
            ```sapl
            policy "example"
            permit action.name == "delegate"
            where
              permissions.isSubsetOf(action.permissionsToDelegate, subject.permissions);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isSubsetOf(NumberValue permissions, NumberValue superset) {
        val permissionsNumber = permissions.value().longValue();
        val supersetNumber    = superset.value().longValue();
        return Value.of((permissionsNumber & supersetNumber) == permissionsNumber);
    }

    @Function(docs = """
            ```overlaps(LONG permissions1, LONG permissions2)```: Checks if two permission sets have any common bits.

            Returns true if at least one bit is set in both permission sets.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              permissions.overlaps(subject.permissions, resource.requiredPermissions);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value overlaps(NumberValue permissions1, NumberValue permissions2) {
        val permissions1Number = permissions1.value().longValue();
        val permissions2Number = permissions2.value().longValue();
        return Value.of((permissions1Number & permissions2Number) != 0);
    }

    @Function(docs = """
            ```areDisjoint(LONG permissions1, LONG permissions2)```: Checks if two permission sets have no common bits.

            Returns true if no bits are set in both permission sets simultaneously.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              permissions.areDisjoint(subject.permissions, resource.forbiddenPermissions);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value areDisjoint(NumberValue permissions1, NumberValue permissions2) {
        val permissions1Number = permissions1.value().longValue();
        val permissions2Number = permissions2.value().longValue();
        return Value.of((permissions1Number & permissions2Number) == 0);
    }

    /* Unix/POSIX Permission Functions */

    @Function(docs = """
            ```unixOwner(LONG mode)```: Extracts the owner permission bits from a Unix file mode.

            Returns the owner permissions as a value from 0-7, where bits represent
            read (4), write (2), and execute (1) permissions.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var mode = numeral.fromOctal("755");
              var ownerPerms = permissions.unixOwner(mode);  // Returns 7 (rwx)
            ```
            """, schema = RETURNS_NUMBER)
    public static Value unixOwner(NumberValue mode) {
        val modeNumber = mode.value().longValue();
        return Value.of((modeNumber >> 6) & 0x7L);
    }

    @Function(docs = """
            ```unixGroup(LONG mode)```: Extracts the group permission bits from a Unix file mode.

            Returns the group permissions as a value from 0-7, where bits represent
            read (4), write (2), and execute (1) permissions.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var mode = numeral.fromOctal("755");
              var groupPerms = permissions.unixGroup(mode);  // Returns 5 (r-x)
            ```
            """, schema = RETURNS_NUMBER)
    public static Value unixGroup(NumberValue mode) {
        val modeNumber = mode.value().longValue();
        return Value.of((modeNumber >> 3) & 0x7L);
    }

    @Function(docs = """
            ```unixOther(LONG mode)```: Extracts the other (world) permission bits from a Unix file mode.

            Returns the other permissions as a value from 0-7, where bits represent
            read (4), write (2), and execute (1) permissions.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var mode = numeral.fromOctal("755");
              var otherPerms = permissions.unixOther(mode);  // Returns 5 (r-x)
            ```
            """, schema = RETURNS_NUMBER)
    public static Value unixOther(NumberValue mode) {
        val modeNumber = mode.value().longValue();
        return Value.of(modeNumber & 0x7L);
    }

    @Function(docs = """
            ```unixMode(LONG owner, LONG group, LONG other)```: Constructs a Unix file mode from separate permission values.

            Combines owner, group, and other permissions (each 0-7) into a complete Unix mode value.

            **Requirements:**
            - owner, group, and other must be between 0 and 7

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var mode = permissions.unixMode(7, 5, 5);
              mode == numeral.fromOctal("755");  // true
            ```
            """, schema = RETURNS_NUMBER)
    public static Value unixMode(NumberValue owner, NumberValue group, NumberValue other) {
        val ownerNumber = owner.value().longValue();
        val groupNumber = group.value().longValue();
        val otherNumber = other.value().longValue();

        if (ownerNumber < 0 || ownerNumber > 7) {
            return Value.error(ERROR_OWNER_PERMISSIONS_INVALID);
        }
        if (groupNumber < 0 || groupNumber > 7) {
            return Value.error(ERROR_GROUP_PERMISSIONS_INVALID);
        }
        if (otherNumber < 0 || otherNumber > 7) {
            return Value.error(ERROR_OTHER_PERMISSIONS_INVALID);
        }

        return Value.of((ownerNumber << 6) | (groupNumber << 3) | otherNumber);
    }

    @Function(docs = """
            ```unixCanRead(LONG permissions)```: Checks if the read permission bit is set in Unix permissions.

            Tests if the read bit (4) is set in a Unix permission value (0-7).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var perms = permissions.unixOwner(resource.mode);
              permissions.unixCanRead(perms);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value unixCanRead(NumberValue permissions) {
        return Value.of((permissions.value().longValue() & 0x4L) != 0);
    }

    @Function(docs = """
            ```unixCanWrite(LONG permissions)```: Checks if the write permission bit is set in Unix permissions.

            Tests if the write bit (2) is set in a Unix permission value (0-7).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var perms = permissions.unixOwner(resource.mode);
              permissions.unixCanWrite(perms);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value unixCanWrite(NumberValue permissions) {
        return Value.of((permissions.value().longValue() & 0x2L) != 0);
    }

    @Function(docs = """
            ```unixCanExecute(LONG permissions)```: Checks if the execute permission bit is set in Unix permissions.

            Tests if the execute bit (1) is set in a Unix permission value (0-7).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var perms = permissions.unixOwner(resource.mode);
              permissions.unixCanExecute(perms);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value unixCanExecute(NumberValue permissions) {
        return Value.of((permissions.value().longValue() & 0x1L) != 0);
    }

    /* POSIX Permission Constants */

    @Function(docs = """
            ```posixRead()```: Returns the POSIX read permission bit value (4).

            Standard read permission bit used in Unix/POSIX file permissions.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              permissions.hasAny(ownerPerms, [permissions.posixRead()]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixRead() {
        return POSIX_READ;
    }

    @Function(docs = """
            ```posixWrite()```: Returns the POSIX write permission bit value (2).

            Standard write permission bit used in Unix/POSIX file permissions.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              permissions.hasAny(ownerPerms, [permissions.posixWrite()]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixWrite() {
        return POSIX_WRITE;
    }

    @Function(docs = """
            ```posixExecute()```: Returns the POSIX execute permission bit value (1).

            Standard execute permission bit used in Unix/POSIX file permissions.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              permissions.hasAny(ownerPerms, [permissions.posixExecute()]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixExecute() {
        return POSIX_EXECUTE;
    }

    @Function(docs = """
            ```posixAll()```: Returns the POSIX value for all permissions (7).

            Represents read + write + execute permissions (4 + 2 + 1 = 7).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              permissions.hasAll(ownerPerms, [permissions.posixAll()]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixAll() {
        return POSIX_ALL;
    }

    @Function(docs = """
            ```posixNone()```: Returns the POSIX value for no permissions (0).

            Represents the absence of read, write, and execute permissions.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              otherPerms == permissions.posixNone();
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixNone() {
        return POSIX_NONE;
    }

    @Function(docs = """
            ```posixRW()```: Returns the POSIX value for read and write permissions (6).

            Represents read + write permissions (4 + 2 = 6).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              ownerPerms == permissions.posixRW();
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixRW() {
        return POSIX_RW;
    }

    @Function(docs = """
            ```posixRX()```: Returns the POSIX value for read and execute permissions (5).

            Represents read + execute permissions (4 + 1 = 5).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              groupPerms == permissions.posixRX();
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixRX() {
        return POSIX_RX;
    }

    @Function(docs = """
            ```posixWX()```: Returns the POSIX value for write and execute permissions (3).

            Represents write + execute permissions (2 + 1 = 3).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              permissions.hasAll(perms, [permissions.posixWX()]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixWX() {
        return POSIX_WX;
    }

    /* Common POSIX Mode Constants */

    @Function(docs = """
            ```posixMode755()```: Returns the common Unix mode 0755 (rwxr-xr-x) as decimal 493.

            Owner: read, write, execute (7). Group: read, execute (5). Other: read, execute (5).
            Commonly used for executable files and directories.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              resource.mode == permissions.posixMode755();
              // Equivalent to: resource.mode == numeral.fromOctal("755");
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixMode755() {
        return POSIX_MODE_755;
    }

    @Function(docs = """
            ```posixMode644()```: Returns the common Unix mode 0644 (rw-r--r--) as decimal 420.

            Owner: read, write (6). Group: read (4). Other: read (4).
            Commonly used for regular data files.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              resource.mode == permissions.posixMode644();
              // Equivalent to: resource.mode == numeral.fromOctal("644");
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixMode644() {
        return POSIX_MODE_644;
    }

    @Function(docs = """
            ```posixMode777()```: Returns the common Unix mode 0777 (rwxrwxrwx) as decimal 511.

            Owner: read, write, execute (7). Group: read, write, execute (7). Other: read, write, execute (7).
            Maximum permissions for all users. Use with caution.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              resource.mode == permissions.posixMode777();
              // Equivalent to: resource.mode == numeral.fromOctal("777");
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixMode777() {
        return POSIX_MODE_777;
    }

    @Function(docs = """
            ```posixMode600()```: Returns the common Unix mode 0600 (rw-------) as decimal 384.

            Owner: read, write (6). Group: none (0). Other: none (0).
            Commonly used for private files like SSH keys.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              resource.mode == permissions.posixMode600();
              // Equivalent to: resource.mode == numeral.fromOctal("600");
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixMode600() {
        return POSIX_MODE_600;
    }

    @Function(docs = """
            ```posixMode666()```: Returns the common Unix mode 0666 (rw-rw-rw-) as decimal 438.

            Owner: read, write (6). Group: read, write (6). Other: read, write (6).
            Commonly used for shared data files.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              resource.mode == permissions.posixMode666();
              // Equivalent to: resource.mode == numeral.fromOctal("666");
            ```
            """, schema = RETURNS_NUMBER)
    public static Value posixMode666() {
        return POSIX_MODE_666;
    }

    /* Permission Manipulation Functions */

    @Function(docs = """
            ```grant(LONG current, ARRAY toGrant)```: Adds specified permissions to the current permission set.

            Combines the permissions to grant using OR, then adds them to the current permissions.
            Semantic wrapper for bitwise OR operations.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var READ = permissions.bit(0);
              var WRITE = permissions.bit(1);
              var newPerms = permissions.grant(subject.currentPermissions, [READ, WRITE]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value grant(NumberValue current, ArrayValue toGrant) {
        val combinedGrant = combineWithOr(toGrant);
        if (combinedGrant instanceof ErrorValue) {
            return combinedGrant;
        }

        val currentNumber = current.value().longValue();
        val grantNumber   = ((NumberValue) combinedGrant).value().longValue();
        return Value.of(currentNumber | grantNumber);
    }

    @Function(docs = """
            ```revoke(LONG current, ARRAY toRevoke)```: Removes specified permissions from the current permission set.

            Combines the permissions to revoke using OR, then removes them from the current permissions.
            Semantic wrapper for bitwise AND NOT operations.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var DELETE = permissions.bit(2);
              var ADMIN = permissions.bit(3);
              var newPerms = permissions.revoke(subject.currentPermissions, [DELETE, ADMIN]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value revoke(NumberValue current, ArrayValue toRevoke) {
        val combinedRevoke = combineWithOr(toRevoke);
        if (combinedRevoke instanceof ErrorValue) {
            return combinedRevoke;
        }

        val currentNumber = current.value().longValue();
        val revokeNumber  = ((NumberValue) combinedRevoke).value().longValue();
        return Value.of(currentNumber & ~revokeNumber);
    }

    @Function(docs = """
            ```toggle(LONG current, ARRAY toToggle)```: Flips specified permission bits in the current permission set.

            Combines the permissions to toggle using OR, then flips them in the current permissions.
            Set bits become unset, and unset bits become set.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var FLAG_A = permissions.bit(0);
              var FLAG_B = permissions.bit(1);
              var newFlags = permissions.toggle(subject.flags, [FLAG_A, FLAG_B]);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value toggle(NumberValue current, ArrayValue toToggle) {
        val combinedToggle = combineWithOr(toToggle);
        if (combinedToggle instanceof ErrorValue) {
            return combinedToggle;
        }

        val currentNumber = current.value().longValue();
        val toggleNumber  = ((NumberValue) combinedToggle).value().longValue();
        return Value.of(currentNumber ^ toggleNumber);
    }

    /* Universal Constants */

    @Function(docs = """
            ```none()```: Returns zero (0), representing no permissions set.

            Semantic constant for the absence of any permissions. All 64 bits are unset.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              subject.permissions != permissions.none();
            ```
            """, schema = RETURNS_NUMBER)
    public static Value none() {
        return NONE;
    }

    @Function(docs = """
            ```all()```: Returns -1, representing all permissions set.

            Semantic constant for maximum permissions. All 64 bits are set.
            In two's complement representation, -1 has all bits set to 1.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              subject.permissions == permissions.all();
            ```
            """, schema = RETURNS_NUMBER)
    public static Value all() {
        return ALL;
    }

    @Function(docs = """
            ```bit(LONG position)```: Returns a value with only the specified bit position set.

            Creates a permission mask with a single bit set at the given position (0-63).
            Useful for defining custom permission schemes where each bit represents
            a specific permission.

            **Requirements:**
            - position must be between 0 and 63

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              var CREATE = permissions.bit(0);
              var READ = permissions.bit(1);
              var UPDATE = permissions.bit(2);
              var DELETE = permissions.bit(3);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value bit(NumberValue position) {
        val positionNumber = position.value().longValue();
        if (positionNumber < 0 || positionNumber >= 64) {
            return Value.error(ERROR_BIT_POSITION_INVALID);
        }
        return Value.of(1L << positionNumber);
    }

    /**
     * Combines an array of long values using bitwise OR.
     *
     * @param masks
     * the array of masks to combine
     *
     * @return a Value containing the combined mask or an errors
     */
    private static Value combineWithOr(ArrayValue masks) {
        var result = 0L;
        for (var element : masks) {
            if (!(element instanceof NumberValue numberValue)) {
                return Value.error(ERROR_MASKS_MUST_BE_NUMBERS);
            }
            if (!isIntegral(numberValue.value())) {
                return Value.error(ERROR_MASKS_MUST_BE_INTEGERS);
            }
            result |= numberValue.value().longValue();
        }

        return Value.of(result);
    }

    /**
     * Combines an array of long values using bitwise AND.
     *
     * @param masks
     * the array of masks to combine
     *
     * @return a Value containing the combined mask or an errors
     */
    private static Value combineWithAnd(ArrayValue masks) {
        if (masks.isEmpty()) {
            return Value.error(ERROR_CANNOT_COMBINE_EMPTY_ARRAY);
        }

        var result = -1L;
        for (var element : masks) {
            if (!(element instanceof NumberValue numberValue)) {
                return Value.error(ERROR_MASKS_MUST_BE_NUMBERS);
            }
            if (!isIntegral(numberValue.value())) {
                return Value.error(ERROR_MASKS_MUST_BE_INTEGERS);
            }
            result &= numberValue.value().longValue();
        }

        return Value.of(result);
    }

    /**
     * Checks if a BigDecimal represents an integral value with no fractional part.
     *
     * @param value
     * the BigDecimal to check
     *
     * @return true if the value is integral, false if it has a fractional part
     */
    private static boolean isIntegral(BigDecimal value) {
        try {
            return value.stripTrailingZeros().scale() <= 0;
        } catch (ArithmeticException e) {
            return true;
        }
    }
}

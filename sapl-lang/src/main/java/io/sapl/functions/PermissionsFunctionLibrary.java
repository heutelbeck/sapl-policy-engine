/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Long;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Permission and access control functions for policy enforcement.
 * Provides semantic operations for checking and manipulating permission bitmasks,
 * with specialized support for POSIX/Unix file permissions.
 * <p/>
 * Functions accept arrays of permission masks and combine them automatically,
 * eliminating the need for verbose manual bitwise operations in policies.
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

    private static final Val POSIX_READ    = Val.of(0x4L);
    private static final Val POSIX_WRITE   = Val.of(0x2L);
    private static final Val POSIX_EXECUTE = Val.of(0x1L);
    private static final Val POSIX_ALL     = Val.of(0x7L);
    private static final Val POSIX_NONE    = Val.of(0x0L);
    private static final Val POSIX_RW      = Val.of(0x6L);
    private static final Val POSIX_RX      = Val.of(0x5L);
    private static final Val POSIX_WX      = Val.of(0x3L);

    @SuppressWarnings("java:S1314")
    private static final Val POSIX_MODE_755 = Val.of(0_755L);
    @SuppressWarnings("java:S1314")
    private static final Val POSIX_MODE_644 = Val.of(0_644L);
    @SuppressWarnings("java:S1314")
    private static final Val POSIX_MODE_777 = Val.of(0_777L);
    @SuppressWarnings("java:S1314")
    private static final Val POSIX_MODE_600 = Val.of(0_600L);
    @SuppressWarnings("java:S1314")
    private static final Val POSIX_MODE_666 = Val.of(0_666L);

    private static final Val NONE = Val.of(0x0L);
    private static final Val ALL  = Val.of(-1L);

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
    public static Val hasAll(@Long Val value, @Array Val masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask.isError()) {
            return combinedMask;
        }

        val valueNumber = value.get().longValue();
        val maskNumber = combinedMask.get().longValue();
        return Val.of((valueNumber & maskNumber) == maskNumber);
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
    public static Val hasAny(@Long Val value, @Array Val masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask.isError()) {
            return combinedMask;
        }

        val valueNumber = value.get().longValue();
        val maskNumber = combinedMask.get().longValue();
        return Val.of((valueNumber & maskNumber) != 0);
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
    public static Val hasNone(@Long Val value, @Array Val masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask.isError()) {
            return combinedMask;
        }

        val valueNumber = value.get().longValue();
        val maskNumber = combinedMask.get().longValue();
        return Val.of((valueNumber & maskNumber) == 0);
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
    public static Val hasExact(@Long Val value, @Long Val mask) {
        return Val.of(value.get().longValue() == mask.get().longValue());
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
    public static Val hasOnly(@Long Val value, @Array Val masks) {
        val combinedMask = combineWithOr(masks);
        if (combinedMask.isError()) {
            return combinedMask;
        }

        val valueNumber = value.get().longValue();
        val maskNumber = combinedMask.get().longValue();
        return Val.of((valueNumber & ~maskNumber) == 0);
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
    public static Val combine(@Array Val masks) {
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
    public static Val combineAll(@Array Val masks) {
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
    public static Val isSubsetOf(@Long Val permissions, @Long Val superset) {
        val permissionsNumber = permissions.get().longValue();
        val supersetNumber = superset.get().longValue();
        return Val.of((permissionsNumber & supersetNumber) == permissionsNumber);
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
    public static Val overlaps(@Long Val permissions1, @Long Val permissions2) {
        val permissions1Number = permissions1.get().longValue();
        val permissions2Number = permissions2.get().longValue();
        return Val.of((permissions1Number & permissions2Number) != 0);
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
    public static Val areDisjoint(@Long Val permissions1, @Long Val permissions2) {
        val permissions1Number = permissions1.get().longValue();
        val permissions2Number = permissions2.get().longValue();
        return Val.of((permissions1Number & permissions2Number) == 0);
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
    public static Val unixOwner(@Long Val mode) {
        val modeNumber = mode.get().longValue();
        return Val.of((modeNumber >> 6) & 0x7L);
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
    public static Val unixGroup(@Long Val mode) {
        val modeNumber = mode.get().longValue();
        return Val.of((modeNumber >> 3) & 0x7L);
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
    public static Val unixOther(@Long Val mode) {
        val modeNumber = mode.get().longValue();
        return Val.of(modeNumber & 0x7L);
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
    public static Val unixMode(@Long Val owner, @Long Val group, @Long Val other) {
        val ownerNumber = owner.get().longValue();
        val groupNumber = group.get().longValue();
        val otherNumber = other.get().longValue();

        if (ownerNumber < 0 || ownerNumber > 7) {
            return Val.error("Owner permissions must be between 0 and 7");
        }
        if (groupNumber < 0 || groupNumber > 7) {
            return Val.error("Group permissions must be between 0 and 7");
        }
        if (otherNumber < 0 || otherNumber > 7) {
            return Val.error("Other permissions must be between 0 and 7");
        }

        return Val.of((ownerNumber << 6) | (groupNumber << 3) | otherNumber);
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
    public static Val unixCanRead(@Long Val permissions) {
        return Val.of((permissions.get().longValue() & 0x4L) != 0);
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
    public static Val unixCanWrite(@Long Val permissions) {
        return Val.of((permissions.get().longValue() & 0x2L) != 0);
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
    public static Val unixCanExecute(@Long Val permissions) {
        return Val.of((permissions.get().longValue() & 0x1L) != 0);
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
    public static Val posixRead() {
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
    public static Val posixWrite() {
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
    public static Val posixExecute() {
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
    public static Val posixAll() {
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
    public static Val posixNone() {
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
    public static Val posixRW() {
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
    public static Val posixRX() {
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
    public static Val posixWX() {
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
    public static Val posixMode755() {
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
    public static Val posixMode644() {
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
    public static Val posixMode777() {
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
    public static Val posixMode600() {
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
    public static Val posixMode666() {
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
    public static Val grant(@Long Val current, @Array Val toGrant) {
        val combinedGrant = combineWithOr(toGrant);
        if (combinedGrant.isError()) {
            return combinedGrant;
        }

        val currentNumber = current.get().longValue();
        val grantNumber = combinedGrant.get().longValue();
        return Val.of(currentNumber | grantNumber);
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
    public static Val revoke(@Long Val current, @Array Val toRevoke) {
        val combinedRevoke = combineWithOr(toRevoke);
        if (combinedRevoke.isError()) {
            return combinedRevoke;
        }

        val currentNumber = current.get().longValue();
        val revokeNumber = combinedRevoke.get().longValue();
        return Val.of(currentNumber & ~revokeNumber);
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
    public static Val toggle(@Long Val current, @Array Val toToggle) {
        val combinedToggle = combineWithOr(toToggle);
        if (combinedToggle.isError()) {
            return combinedToggle;
        }

        val currentNumber = current.get().longValue();
        val toggleNumber = combinedToggle.get().longValue();
        return Val.of(currentNumber ^ toggleNumber);
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
    public static Val none() {
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
    public static Val all() {
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
    public static Val bit(@Long Val position) {
        val positionNumber = position.get().longValue();
        if (positionNumber < 0 || positionNumber >= 64) {
            return Val.error("Bit position must be between 0 and 63");
        }
        return Val.of(1L << positionNumber);
    }

    /**
     * Combines an array of long values using bitwise OR.
     *
     * @param masks the array of masks to combine
     * @return a Val containing the combined mask or an error
     */
    private static Val combineWithOr(Val masks) {
        if (!masks.get().isArray()) {
            return Val.error("Expected array of permission masks");
        }

        var result = 0L;
        for (JsonNode element : masks.get()) {
            if (!element.isIntegralNumber()) {
                return Val.error("All permission masks must be integers");
            }
            result |= element.longValue();
        }

        return Val.of(result);
    }

    /**
     * Combines an array of long values using bitwise AND.
     *
     * @param masks the array of masks to combine
     * @return a Val containing the combined mask or an error
     */
    private static Val combineWithAnd(Val masks) {
        if (!masks.get().isArray()) {
            return Val.error("Expected array of permission masks");
        }

        if (masks.get().isEmpty()) {
            return Val.error("Cannot combine empty array with AND");
        }

        var result = -1L;
        for (JsonNode element : masks.get()) {
            if (!element.isIntegralNumber()) {
                return Val.error("All permission masks must be integers");
            }
            result &= element.longValue();
        }

        return Val.of(result);
    }
}
/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <string.h>
#include <memory.h>

#include <atomic>
#include <cstdint>

namespace android::app::PropertyInvalidatedCache {

/**
 * A head of a CacheNonce object.  This contains all the fields that have a fixed size and
 * location.  Fields with a variable location are found via offsets.  The offsets make this
 * object position-independent, which is required because it is in shared memory and would be
 * mapped into different virtual addresses for different processes.
 *
 * This structure is shared between 64-bit and 32-bit processes.  Therefore it is imperative
 * that the structure not use any datatypes that are architecture-dependent (like size_t).
 * Additionally, care must be taken to avoid unexpected padding in the structure.
 */
class alignas(8) NonceStore {
  protected:
    // A convenient typedef.  The jbyteArray element type is jbyte, which the compiler treats as
    // signed char.
    typedef signed char block_t;

    // The nonce type.
    typedef std::atomic<int64_t> nonce_t;

    // Atomics should be safe to use across processes if they are lock free.
    static_assert(nonce_t::is_always_lock_free == true);

    // The value of an unset field.
    static constexpr int UNSET = 0;

    // The size of the nonce array.  This and the following sizes are int32_t to
    // be ABI independent.
    const int32_t kMaxNonce;

    // The size of the byte array.
    const int32_t kMaxByte;

    // The offset to the nonce array.
    const int32_t mNonceOffset;

    // The offset to the byte array.
    const int32_t mByteOffset;

    // The byte block hash.  This is fixed and at a known offset, so leave it in the base class.
    volatile std::atomic<int32_t> mByteHash;

    // A 4-byte padd that makes the size of this structure a multiple of 8 bytes.
    const int32_t _pad = 0;

    // The constructor is protected!  It only makes sense when called from a subclass.
    NonceStore(int kMaxNonce, int kMaxByte, volatile nonce_t* nonce, volatile block_t* block) :
            kMaxNonce(kMaxNonce),
            kMaxByte(kMaxByte),
            mNonceOffset(offset(this, const_cast<nonce_t*>(nonce))),
            mByteOffset(offset(this, const_cast<block_t*>(block))) {
    }

  public:

    // These provide run-time access to the sizing parameters.
    int getMaxNonce() const;
    int getMaxByte() const;

    // Fetch a nonce, returning UNSET if the index is out of range.  This method specifically
    // does not throw or generate an error if the index is out of range; this allows the method
    // to be called in a CriticalNative JNI API.
    int64_t getNonce(int index) const;

    // Set a nonce and return true. Return false if the index is out of range.  This method
    // specifically does not throw or generate an error if the index is out of range; this
    // allows the method to be called in a CriticalNative JNI API.
    bool setNonce(int index, int64_t value);

    // Fetch just the byte-block hash
    int32_t getHash() const;

    // Copy the byte block to the target and return the current hash.
    int32_t getByteBlock(block_t* block, int32_t len) const;

    // Set the byte block and the hash.
    void setByteBlock(int hash, const block_t* block, int32_t len);

  private:

    // A convenience function to compute the offset between two unlike pointers.
    static size_t offset(void const* base, void const* member) {
        return reinterpret_cast<uintptr_t>(member) - reinterpret_cast<std::uintptr_t>(base);
    }

    // Return the address of the nonce array.
    volatile nonce_t* nonce() const {
        // The array is located at an offset from <this>.
        return reinterpret_cast<nonce_t*>(
            reinterpret_cast<std::uintptr_t>(this) + mNonceOffset);
    }

    // Return the address of the byte block array.
    volatile block_t* byteBlock() const {
        // The array is located at an offset from <this>.
        return reinterpret_cast<block_t*>(
            reinterpret_cast<std::uintptr_t>(this) + mByteOffset);
    }
};

// Assert that the size of the object is fixed, independent of the CPU architecture.  There are
// four int32_t fields and one atomic<int32_t>, which sums to 20 bytes total.  This assertion
// uses a constant instead of computing the size of the objects in the compiler, to avoid
// different answers on different architectures.
static_assert(sizeof(NonceStore) == 24);

/**
 * A cache nonce block contains an array of std::atomic<int64_t> and an array of bytes.  The
 * byte array has an associated hash.  This class provides methods to read and write the fields
 * of the block but it does not interpret the fields.
 *
 * On initialization, all fields are set to zero.
 *
 * In general, methods do not report errors.  This allows the methods to be used in
 * CriticalNative JNI APIs.
 *
 * The template is parameterized by the number of nonces it supports and the number of bytes in
 * the string block.
 */
template<int MAX_NONCE, int MAX_BYTE> class CacheNonce : public NonceStore {

    // The array of nonces
    volatile nonce_t mNonce[MAX_NONCE];

    // The byte array.  This is not atomic but it is guarded by the mByteHash.
    volatile block_t mByteBlock[MAX_BYTE];

  public:
    // Export the parameters for use in compiler assertions.
    static constexpr int kMaxNonceCount = MAX_NONCE;
    static constexpr int kMaxByteCount = MAX_BYTE;

    // Construct and initialize the memory.
    CacheNonce() : NonceStore(MAX_NONCE, MAX_BYTE, &mNonce[0], &mByteBlock[0]) {
        for (int i = 0; i < MAX_NONCE; i++) {
            mNonce[i] = UNSET;
        }
        mByteHash = UNSET;
        memset((void*) mByteBlock, UNSET, sizeof(mByteBlock));
    }
};

// The CacheNonce for system server.  The configuration values are not defined as visible
// constants.  Clients should use the accessors on the SystemCacheNonce instance if they need
// the sizing parameters.

// LINT.IfChange(system_nonce_config)
typedef CacheNonce</* max nonce */ 128, /* byte block size */ 8192> SystemCacheNonce;
// LINT.ThenChange(/core/tests/coretests/src/android/app/PropertyInvalidatedCacheTests.java:system_nonce_config)

// Verify that there is no padding in the final class.
static_assert(sizeof(SystemCacheNonce) ==
              sizeof(NonceStore)
              + SystemCacheNonce::kMaxNonceCount*8
              + SystemCacheNonce::kMaxByteCount);

} // namespace android.app.PropertyInvalidatedCache

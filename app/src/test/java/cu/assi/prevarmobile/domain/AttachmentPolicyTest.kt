package cu.assi.prevarmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AttachmentPolicyTest {
    @Test fun pathTraversalIsRemoved() {
        assertEquals("secret.pdf", AttachmentPolicy.sanitizeFileName("../../secret.pdf"))
        assertEquals("photo.jpg", AttachmentPolicy.sanitizeFileName("..\\..\\photo.jpg"))
    }

    @Test fun imagesAndPdfAreAllowed() {
        AttachmentPolicy.validateMime("image/jpeg")
        AttachmentPolicy.validateMime("application/pdf")
    }

    @Test fun executableMimeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentPolicy.validateMime("application/x-msdownload")
        }
    }

    @Test fun oversizedAttachmentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentPolicy.validateSize(AttachmentPolicy.MAX_BYTES + 1)
        }
    }
}

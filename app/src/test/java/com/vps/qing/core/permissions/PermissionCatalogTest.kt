package com.vps.qing.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.4.17 权限合规体系：默认全关、分组正确、只申请用户勾选的运行时权限。 */
class PermissionCatalogTest {

    @Test
    fun `七项权限分组正确且全部默认关闭`() {
        assertEquals(7, PermissionCatalog.ALL.size)
        assertEquals(2, PermissionCatalog.REQUIRED.size)
        assertEquals(5, PermissionCatalog.OPTIONAL.size)
        assertTrue("所有权限必须默认关闭", PermissionCatalog.ALL.all { !it.defaultEnabled })
        assertTrue(
            "每项都要写清用途与不开通影响（知情权）",
            PermissionCatalog.ALL.all { it.purpose.isNotBlank() && it.deniedImpact.isNotBlank() }
        )
        assertEquals(PermissionCatalog.ALL.size, PermissionCatalog.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `必要权限与可选权限内容符合需求`() {
        assertEquals(
            setOf(PermissionCatalog.NETWORK, PermissionCatalog.STORAGE),
            PermissionCatalog.REQUIRED.map { it.id }.toSet()
        )
        assertEquals(
            setOf(
                PermissionCatalog.NOTIFICATIONS, PermissionCatalog.LOCATION,
                PermissionCatalog.EXTERNAL_APPS, PermissionCatalog.CLIPBOARD,
                PermissionCatalog.CAMERA
            ),
            PermissionCatalog.OPTIONAL.map { it.id }.toSet()
        )
        // 需求里「定位权限」写了两次，实现里必须去重
        assertEquals(1, PermissionCatalog.ALL.count { it.id == PermissionCatalog.LOCATION })
    }

    @Test
    fun `未同意任何权限时不申请任何系统权限`() {
        assertTrue(PermissionCatalog.runtimePermissionsFor(emptySet()).isEmpty())
    }

    @Test
    fun `只申请勾选的运行时权限且不重复申请安装期权限`() {
        val perms = PermissionCatalog.runtimePermissionsFor(
            setOf(PermissionCatalog.CAMERA, PermissionCatalog.NETWORK)
        )
        assertTrue(perms.contains(android.Manifest.permission.CAMERA))
        assertFalse("INTERNET 是安装期权限，不应出现在系统申请里",
            perms.contains(android.Manifest.permission.INTERNET))
        assertFalse(perms.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
        assertEquals(perms.size, perms.toSet().size)
    }

    @Test
    fun `应用内开关类权限不触发系统弹窗`() {
        assertTrue(PermissionCatalog.runtimePermissions(
            PermissionCatalog.byId(PermissionCatalog.EXTERNAL_APPS)!!
        ).isEmpty())
        assertTrue(PermissionCatalog.runtimePermissions(
            PermissionCatalog.byId(PermissionCatalog.CLIPBOARD)!!
        ).isEmpty())
    }
}

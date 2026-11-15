package com.vps.qing.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.19：子账户权限树勾选状态机（纯 JVM）。
 * 协议来自站点 addChild.js：提交 auth = getCheckedKeys + getHalfCheckedKeys
 * （选中叶子 + 半选/全选父链），「概要」及其祖先必选。
 */
class AuthTreeSelectionTest {

    /** 一棵三层树：概要(必选) / 产品管理(下单、续费) / 财务(查看、充值)。 */
    private fun tree() = listOf(
        AuthNode(
            id = "1", title = "首页",
            children = listOf(
                AuthNode(id = "1-1", title = "概要"),
                AuthNode(id = "1-2", title = "公告")
            )
        ),
        AuthNode(
            id = "2", title = "产品管理",
            children = listOf(
                AuthNode(id = "2-1", title = "下单"),
                AuthNode(id = "2-2", title = "续费")
            )
        ),
        AuthNode(
            id = "3", title = "财务",
            children = listOf(
                AuthNode(id = "3-1", title = "查看余额"),
                AuthNode(
                    id = "3-2", title = "充值",
                    children = listOf(
                        AuthNode(id = "3-2-1", title = "在线充值"),
                        AuthNode(id = "3-2-2", title = "充值记录")
                    )
                )
            )
        )
    )

    @Test
    fun `叶子集合覆盖所有层级`() {
        val leaves = AuthTreeSelection.allLeafIds(tree())
        assertEquals(setOf("1-1", "1-2", "2-1", "2-2", "3-1", "3-2-1", "3-2-2"), leaves)
    }

    @Test
    fun `概要及其祖先链必选`() {
        val m = AuthTreeSelection.mandatoryPathIds(tree())
        assertTrue("1" in m)
        assertTrue("1-1" in m)
        assertFalse("2" in m)
    }

    @Test
    fun `三态由叶子选中比例派生`() {
        val root = tree()[1] // 产品管理
        assertEquals(
            AuthTreeSelection.State.OFF,
            AuthTreeSelection.stateOf(root, emptySet())
        )
        assertEquals(
            AuthTreeSelection.State.INDETERMINATE,
            AuthTreeSelection.stateOf(root, setOf("2-1"))
        )
        assertEquals(
            AuthTreeSelection.State.ON,
            AuthTreeSelection.stateOf(root, setOf("2-1", "2-2"))
        )
    }

    @Test
    fun `整组开关生成选中叶子加父链`() {
        val t = tree()
        val r1 = AuthTreeSelection.toggle(t, emptyList(), "2") // 全开「产品管理」
        assertTrue("2" in r1)
        assertTrue("2-1" in r1)
        assertTrue("2-2" in r1)
        // 概要必选路径无条件在结果里
        assertTrue("1" in r1)
        assertTrue("1-1" in r1)
        // 未触碰的分组不应出现
        assertFalse("3" in r1)

        val r2 = AuthTreeSelection.toggle(t, r1, "2") // 再点一次整组取消
        assertFalse("2" in r2)
        assertFalse("2-1" in r2)
        assertFalse("2-2" in r2)
        // 概要仍保底
        assertTrue("1-1" in r2)
    }

    @Test
    fun `逐项勾选叶子产生半选父链`() {
        val t = tree()
        val r = AuthTreeSelection.toggle(t, emptyList(), "3-2-1") // 只勾「在线充值」
        assertTrue("3-2-1" in r)          // 选中叶子
        assertTrue("3-2" in r)            // 半选父
        assertTrue("3" in r)              // 半选祖父
        assertEquals(
            AuthTreeSelection.State.INDETERMINATE,
            AuthTreeSelection.stateOf(t[2], r.toSet())
        )
        assertFalse("3-1" in r)
    }

    @Test
    fun `父节点全部叶子选中时派生 ON`() {
        val t = tree()
        var r = AuthTreeSelection.toggle(t, emptyList(), "3-1")
        r = AuthTreeSelection.toggle(t, r, "3-2") // 整组充值（含两个叶子）
        assertEquals(
            AuthTreeSelection.State.ON,
            AuthTreeSelection.stateOf(t[2], r.toSet())
        )
        assertTrue("3" in r)
    }

    @Test
    fun `概要必选项不可被取消`() {
        val t = tree()
        var r = AuthTreeSelection.toggle(t, emptyList(), "1") // 试图整组取消首页
        // 概要叶子与其祖先链始终保留
        assertTrue("1-1" in r)
        assertTrue("1" in r)
        r = AuthTreeSelection.toggle(t, r, "1-1")            // 直接点必选叶子
        assertTrue("1-1" in r)
    }

    @Test
    fun `编辑回填的完整 auth 可直接作为状态输入并保真 normalize`() {
        val t = tree()
        // 模拟站点回填：叶子 + 半选父链 + 概要路径
        val fromServer = listOf("1", "1-1", "3", "3-2", "3-2-1")
        val normalized = AuthTreeSelection.normalize(t, fromServer.toSet())
        assertTrue("3-2-1" in normalized)
        assertTrue("3-2" in normalized)
        assertTrue("3" in normalized)
        assertTrue("1-1" in normalized) // 概要必选保底
        // 树外 id 原样保留，不丢站点隐含项
        assertTrue("external-99" in AuthTreeSelection.normalize(t, setOf("external-99")))
    }
}

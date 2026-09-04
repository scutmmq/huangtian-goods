# 算法通关：回溯算法题型全解与 Java 标准模板 (治好混乱必读版)

> **导读与痛点剖析**：  
> 回溯法（Backtracking）本质上是**在决策树上进行的深度优先搜索（DFS）**。  
> 很多同学“刷多了反而乱”，核心原因在于**没有理清决策树的形态**——到底是“组合”还是“排列”？元素“能否重复选取”？原数组“是否含有重复元素”？到底是用 `startIndex` 还是 `used[]` 数组？  
> 本篇文档提炼出一套**终极辨析法则**与**各类题型的标准化 Java 工业级代码模板**，帮你建立统一的心智模型，做到“见题即知树形，提笔即成模板”。

---

## 📑 目录索引 (Table of Contents)

- [一、核心心法：一张图看透回溯本质](#一核心心法一张图看透回溯本质)
  - [1.1 树形思维模型：树枝 vs 树层](#11-树形思维模型树枝-vs-树层)
  - [1.2 回溯三部曲标准伪代码](#12-回溯三部曲标准伪代码)
- [二、终极辨析表：专治“做题搞混”](#二终极辨析表专治做题搞混)
  - [2.1 六大核心维度横向速查表](#21-六大核心维度横向速查表)
  - [2.2 核心痛点一：为什么有时候用 `startIndex`，有时候不用？](#22-核心痛点一为什么有时候用-startindex有时候不用)
  - [2.3 核心痛点二：树枝去重 vs 树层去重（最容易懵的地方）](#23-核心痛点二树枝去重-vs-树层去重最容易懵的地方)
  - [2.4 核心痛点三：返回值到底写 `void` 还是 `boolean`？](#24-核心痛点三返回值到底写-void-还是-boolean)
- [三、题型一：组合问题 (Combinations)](#三题型一组合问题-combinations)
  - [模板 1.1：元素无重不可复选 + 固定长度 $k$（LeetCode 77. 组合）](#模板-11元素无重不可复选--固定长度-kleetcode-77-组合)
  - [模板 1.2：元素无重不可复选 + 目标和 $n$（LeetCode 216. 组合总和 III）](#模板-12元素无重不可复选--目标和-nleetcode-216-组合总和-iii)
  - [模板 1.3：元素无重但可无限次复选（LeetCode 39. 组合总和）](#模板-13元素无重但可无限次复选leetcode-39-组合总和)
  - [模板 1.4：元素有重复且不可复选（LeetCode 40. 组合总和 II · 树层去重）](#模板-14元素有重复且不可复选leetcode-40-组合总和-ii--树层去重)
- [四、题型二：子集问题 (Subsets)](#四题型二子集问题-subsets)
  - [模板 2.1：元素无重不可复选（LeetCode 78. 子集）](#模板-21元素无重不可复选leetcode-78-子集)
  - [模板 2.2：元素有重不可复选（LeetCode 90. 子集 II · 树层去重）](#模板-22元素有重不可复选leetcode-90-子集-ii--树层去重)
  - [模板 2.3：不可排序的递增子序列（LeetCode 491. 非递减子序列 · 单层Set去重）](#模板-23不可排序的递增子序列leetcode-491-非递减子序列--单层set去重)
- [五、题型三：排列问题 (Permutations)](#五题型三排列问题-permutations)
  - [模板 3.1：元素无重不可复选（LeetCode 46. 全排列）](#模板-31元素无重不可复选leetcode-46-全排列)
  - [模板 3.2：元素有重不可复选（LeetCode 47. 全排列 II · 树层去重）](#模板-32元素有重不可复选leetcode-47-全排列-ii--树层去重)
- [六、题型四：分割问题 (Partitioning)](#六题型四分割问题-partitioning)
  - [模板 4.1：字符串分割为回文子串（LeetCode 131. 分割回文串）](#模板-41字符串分割为回文子串leetcode-131-分割回文串)
  - [模板 4.2：数字字符串恢复 IP 地址（LeetCode 93. 复原 IP 地址）](#模板-42数字字符串恢复-ip-地址leetcode-93-复原-ip-地址)
- [七、题型五：棋盘与网格搜索 (Board & Grid)](#七题型五棋盘与网格搜索-board--grid)
  - [模板 5.1：逐行决策棋盘（LeetCode 51. N 皇后 · void全量搜索）](#模板-51逐行决策棋盘leetcode-51-n-皇后--void全量搜索)
  - [模板 5.2：二维空格填数字（LeetCode 37. 解数独 · boolean熔断搜索）](#模板-52二维空格填数字leetcode-37-解数独--boolean熔断搜索)
  - [模板 5.3：二维网格四向搜索（LeetCode 79. 单词搜索 · 原地修改回溯）](#模板-53二维网格四向搜索leetcode-79-单词搜索--原地修改回溯)
- [八、进阶：集合划分与桶视角 (Bucket Partition)](#八进阶集合划分与桶视角-bucket-partition)
  - [模板 6.1：划分为 K 个相等的子集（LeetCode 698 · 桶视角剪枝）](#模板-61划分为-k-个相等的子集leetcode-698--桶视角剪枝)
- [九、高频致命陷阱与编码规范](#九高频致命陷阱与编码规范)
  - [9.1 引用传递与“空集合”惨案](#91-引用传递与空集合惨案)
  - [9.2 状态回退与对称性：做选择与撤销选择](#92-状态回退与对称性做选择与撤销选择)
  - [9.3 性能利器：`Deque` vs `ArrayList` vs `int[]`](#93-性能利器deque-vs-arraylist-vs-int)
- [十、回溯刷题路线推荐（先简后难闭环）](#十回溯刷题路线推荐先简后难闭环)

---

## 一、核心心法：一张图看透回溯本质

### 1.1 树形思维模型：树枝 vs 树层

所有回溯问题都可以抽象为一棵**多叉树（决策树）**：
- **树的宽度（横向）**：由当前节点可供选择的候选集大小决定，代码中表现为 **`for` 循环**。
- **树的深度（纵向）**：由做出选择后的递进深度决定，代码中表现为 **递归调用（`backtrack(...)`）**。

```
                       根节点 (空选择)
                 /           |           \
            选择1           选择2          选择3      <-- 树层 (for 循环横向遍历)
           /     \         /     \         /     \
        选择1-1 选择1-2  选择2-1 选择2-2  选择3-1 选择3-2  <-- 树枝 (递归纵向深入)
          ...     ...      ...     ...      ...     ...
```

### 1.2 回溯三部曲标准伪代码

```java
void backtrack(参数, 状态变量) {
    if (终止条件) {
        收集结果;
        return;
    }

    for (元素 : 本层候选集) {
        // 剪枝逻辑
        if (当前选择不满足条件) continue;

        做选择 (更新 path, 标记 used / visited);
        backtrack(更新后的参数); // 递归进入下一层决策树
        撤销选择 (恢复现场: path 弹出末尾, 清除 used / visited 标记);
    }
}
```

---

## 二、终极辨析表：专治“做题搞混”

很多同学觉得混乱，是因为没搞清**各种题型约束条件变化时，控制变量如何改变**。下面用一张全景图和核心辨析表帮你彻底理顺。

### 2.1 六大核心维度横向速查表

| 题型分类 | 题目举例 | 元素是否有重 | 元素能否复选 | 循环起点设计 | 去重逻辑 | 结果收集时机 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **组合（基础）** | LC 77 组合 | 无重 | 不可复选 | `i = startIndex`，递归传 `i + 1` | 无需去重 | 仅叶子节点（`path.size() == k`） |
| **组合（求和）** | LC 216 组合总和 III | 无重 | 不可复选 | `i = startIndex`，递归传 `i + 1` | 无需去重 | 仅叶子节点（`sum == n && size == k`） |
| **组合（无限选）** | LC 39 组合总和 | 无重 | **可无限复选** | `i = startIndex`，递归传 **`i`** | 无需去重 | 满足和为目标值（`target == 0`） |
| **组合（有重复）** | LC 40 组合总和 II | **有重** | 不可复选 | `i = startIndex`，递归传 `i + 1` | **先排序** + 树层去重：`i > startIndex && nums[i] == nums[i-1]` | 满足和为目标值（`target == 0`） |
| **子集（基础）** | LC 78 子集 | 无重 | 不可复选 | `i = startIndex`，递归传 `i + 1` | 无需去重 | **每个节点都收集**（方法开头即 `res.add`） |
| **子集（有重复）** | LC 90 子集 II | **有重** | 不可复选 | `i = startIndex`，递归传 `i + 1` | **先排序** + 树层去重：`i > startIndex && nums[i] == nums[i-1]` | **每个节点都收集**（方法开头即 `res.add`） |
| **递增子序列** | LC 491 非递减子序列 | 有重 | 不可复选 | `i = startIndex`，递归传 `i + 1` | **不可排序**！单层使用 `HashSet` 记录已用元素 | `path.size() >= 2` 时每个合法节点收集 |
| **排列（基础）** | LC 46 全排列 | 无重 | 不可复选 | **固定 `i = 0`**（不用 `startIndex`） | 树枝去重：`used[i] == true` 跳过 | `path.size() == nums.length` |
| **排列（有重复）** | LC 47 全排列 II | **有重** | 不可复选 | **固定 `i = 0`**（不用 `startIndex`） | **先排序** + 树枝去重 + 树层去重：`!used[i-1]` | `path.size() == nums.length` |
| **分割问题** | LC 131 分割回文串 | - | 相当于组合 | `i = startIndex`（切割线） | 校验 `[startIndex, i]` 子串是否合法 | 切割线到达字符串末尾 |
| **棋盘全解** | LC 51 N 皇后 | - | 行列互斥 | 递归传 `row + 1`，列 `col = 0..n-1` | 校验列、对角线是否冲突 | 成功放置到最后一行（`row == n`） |
| **棋盘单解** | LC 37 解数独 | - | 唯一有效解 | 双重循环扫描 `.`，试填 `1..9` | 校验行、列、3x3 宫格 | **返回 `boolean`**，填满返回 `true` 熔断 |

---

### 2.2 核心痛点一：为什么有时候用 `startIndex`，有时候不用？

- **组合 / 子集 / 分割问题（无序性）**：
  - 特征：`[1, 2]` 和 `[2, 1]` 算**同一个集合**。
  - 必须引入 **`startIndex`**：为了避免生成重复的无序集合，下一层递归只能从当前已选元素的**后面**继续选，不能回头，因此 `for (int i = startIndex; i < n; i++)`。
- **排列问题（有序性）**：
  - 特征：`[1, 2]` 和 `[2, 1]` 是**两个不同的排列**。
  - 每次选择都可以从数组头部重新挑选，所以循环**永远从 `i = 0` 开始**。
  - 为了防止“同一个位置的数字在单条路径上被使用多次”，使用 **`boolean[] used`** 记录哪些位置已被挑选。

---

### 2.3 核心痛点二：树枝去重 vs 树层去重（最容易懵的地方）

当原数组有重复元素（如 `[1, 2, 2]`）时，最容易出现重复解。

```
                    根节点 []
            /           |           \
         nums[0]=1   nums[1]=2    nums[2]=2 (与前一个相同，同层跳过!)
        /         \       |
     [1, 2]     [1, 2]  [2, 2]
                (同层重复!)
```

#### 概念区分：
1. **树枝去重（纵向）**：
   - 场景：在同一条递归路径深处，不能选**同一个位置（下标）**的元素。
   - 实现：`used[i] == true` 说明本分支已经在用它，跳过。
2. **树层去重（横向）**：
   - 场景：在同一父节点的所有同级兄弟分支中，数值相同的元素只能由**第一个**分支去展开，后续相同的数值必须跳过，否则会产生完全一模一样的子树。
   - 实现：**必须先对原数组排序 `Arrays.sort(nums)`**！
     - 组合/子集（基于 `startIndex`）：`if (i > startIndex && nums[i] == nums[i - 1]) continue;`
     - 排列（基于 `used` 数组）：`if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) continue;`

> **为什么是 `!used[i - 1]`？**  
> `used[i - 1] == false` 说明前一个相同的元素已经经历了“选取 -> 递归 -> 回溯撤销”，此时我们正处于该元素的**同层下一个兄弟分支**，必须跳过！  
> 如果 `used[i - 1] == true`，说明前一个相同元素正在当前树枝的上一层，两者处于纵向上下级关系（树枝），是合法的一对相同的数。

---

### 2.4 核心痛点三：返回值到底写 `void` 还是 `boolean`？

1. **`void`（全量穷举）**：
   - 绝大多数题目：要求**找出所有的组合、子集、排列、棋盘摆法**。
   - 必须遍历整棵决策树，收集所有可行解，所以不需要返回值，直接遍历完所有分支。
2. **`boolean`（单解熔断 / 提前终止）**：
   - 典型题目：**LeetCode 37 解数独**、**LeetCode 79 单词搜索**。
   - 只要找到**任意一组可行解**，立即通过 `return true` 向上层递归逐级熔断返回，不再执行后续回溯撤销和无效搜索！
   - 模板范式：
     ```java
     if (backtrack(...)) {
         return true; // 命中解，直接向上传递，停止后续穷举
     }
     ```

---

## 三、题型一：组合问题 (Combinations)

### 模板 1.1：元素无重不可复选 + 固定长度 $k$（LeetCode 77. 组合）

- **题目**：给定两个整数 $n$ 和 $k$，返回范围 $[1, n]$ 中所有可能的 $k$ 个数的组合。
- **关键优化**：**剪枝**。如果剩余候选元素数量不足以凑齐 $k$ 个，提前退出循环。
  - 还需要的元素个数：$k - \text{path.size()}$
  - 循环上限调整为：$n - (k - \text{path.size()}) + 1$

```java
import java.util.ArrayList;
import java.util.List;

public class Solution77 {
    public List<List<Integer>> combine(int n, int k) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        backtrack(n, k, 1, path, res);
        return res;
    }

    private void backtrack(int n, int k, int startIndex, List<Integer> path, List<List<Integer>> res) {
        // 1. 终止条件：路径大小达到 k
        if (path.size() == k) {
            res.add(new ArrayList<>(path)); // 深拷贝
            return;
        }

        // 2. 剪枝优化：i 最多只能到 n - (k - path.size()) + 1
        for (int i = startIndex; i <= n - (k - path.size()) + 1; i++) {
            path.add(i);                            // 做选择
            backtrack(n, k, i + 1, path, res);      // 递归：下一层从 i + 1 开始，保证元素不重复
            path.remove(path.size() - 1);           // 撤销选择（恢复现场）
        }
    }
}
```

---

### 模板 1.2：元素无重不可复选 + 目标和 $n$（LeetCode 216. 组合总和 III）

- **题目**：找出所有相加之和为 $n$ 的 $k$ 个数的组合，只使用数字 $1$ 到 $9$，每个数字最多使用一次。
- **关键剪枝**：如果当前路径和已超过 $n$，或当前长度已达到 $k$ 但和不匹配，直接 `return`。

```java
import java.util.ArrayList;
import java.util.List;

public class Solution216 {
    public List<List<Integer>> combinationSum3(int k, int n) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        backtrack(k, n, 0, 1, path, res);
        return res;
    }

    private void backtrack(int k, int targetSum, int currentSum, int startIndex, 
                           List<Integer> path, List<List<Integer>> res) {
        // 剪枝 1：当前和已超标
        if (currentSum > targetSum) {
            return;
        }

        // 终止条件
        if (path.size() == k) {
            if (currentSum == targetSum) {
                res.add(new ArrayList<>(path));
            }
            return;
        }

        // 剪枝 2：候选数量剪枝，数字最大为 9
        for (int i = startIndex; i <= 9 - (k - path.size()) + 1; i++) {
            path.add(i);
            backtrack(k, targetSum, currentSum + i, i + 1, path, res);
            path.remove(path.size() - 1);
        }
    }
}
```

---

### 模板 1.3：元素无重但可无限次复选（LeetCode 39. 组合总和）

- **题目**：无重复正整数数组 `candidates` 和目标值 `target`，找出所有可以使数字和为 `target` 的不同组合，**同一个数字可以无限制重复被选取**。
- **关键点**：
  1. 下一次递归的 `startIndex` 仍然传入 **`i`**（允许重复选取当前元素），而不是 `i + 1`。
  2. 预先将数组升序排序 `Arrays.sort(candidates)`，当 `candidates[i] > target` 时直接 `break`，剪掉所有后续更大元素的无效分支。

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Solution39 {
    public List<List<Integer>> combinationSum(int[] candidates, int target) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        // 必须先排序，方便后续剪枝
        Arrays.sort(candidates);
        backtrack(candidates, target, 0, path, res);
        return res;
    }

    private void backtrack(int[] candidates, int remain, int startIndex, 
                           List<Integer> path, List<List<Integer>> res) {
        // 终止条件：凑满目标和
        if (remain == 0) {
            res.add(new ArrayList<>(path));
            return;
        }

        for (int i = startIndex; i < candidates.length; i++) {
            // 剪枝：因为排过序，如果当前值已经大于剩余值，后面的更大，直接终止本层循环
            if (candidates[i] > remain) {
                break;
            }

            path.add(candidates[i]);
            // 关键：传入 i 而非 i + 1，允许当前元素在下一层继续复选
            backtrack(candidates, remain - candidates[i], i, path, res);
            path.remove(path.size() - 1);
        }
    }
}
```

---

### 模板 1.4：元素有重复且不可复选（LeetCode 40. 组合总和 II · 树层去重）

- **题目**：给定一个可能有重复元素的数组 `candidates` 和一个目标数 `target`，找出所有唯一的数字和为 `target` 的组合。每个数字在每个组合中只能使用一次。
- **解题精髓**：
  - **排序**：`Arrays.sort(candidates)`
  - **树层去重**：`if (i > startIndex && candidates[i] == candidates[i - 1]) continue;`

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Solution40 {
    public List<List<Integer>> combinationSum2(int[] candidates, int target) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        // 核心第一步：排序
        Arrays.sort(candidates);
        backtrack(candidates, target, 0, path, res);
        return res;
    }

    private void backtrack(int[] candidates, int remain, int startIndex, 
                           List<Integer> path, List<List<Integer>> res) {
        if (remain == 0) {
            res.add(new ArrayList<>(path));
            return;
        }

        for (int i = startIndex; i < candidates.length; i++) {
            // 剪枝
            if (candidates[i] > remain) {
                break;
            }

            // 树层去重：同一树层如果遇到相同数值，跳过！
            // i > startIndex 严格保证了是“同一层级中的后续兄弟节点”
            if (i > startIndex && candidates[i] == candidates[i - 1]) {
                continue;
            }

            path.add(candidates[i]);
            backtrack(candidates, remain - candidates[i], i + 1, path, res); // 不可复选，传 i + 1
            path.remove(path.size() - 1);
        }
    }
}
```

---

## 四、题型二：子集问题 (Subsets)

> 💡 **子集 vs 组合的最关键区别**：  
> - **组合**只收集叶子节点（达到固定长度或特定目标和时才收集）。  
> - **子集**收集**整棵决策树上的所有节点**！因此，在递归方法刚进来的第一行，**无条件收集 `res.add(new ArrayList<>(path))`**。

### 模板 2.1：元素无重不可复选（LeetCode 78. 子集）

- **题目**：给你一个整数数组 `nums`，数组中的元素互不相同，返回该数组所有可能的子集（幂集）。

```java
import java.util.ArrayList;
import java.util.List;

public class Solution78 {
    public List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        backtrack(nums, 0, path, res);
        return res;
    }

    private void backtrack(int[] nums, int startIndex, List<Integer> path, List<List<Integer>> res) {
        // 关键点：进入函数立即无条件收集结果！每个节点都是一个有效子集（包括空集）
        res.add(new ArrayList<>(path));

        // 如果 startIndex >= nums.length，for 循环自然不会执行，直接 return
        for (int i = startIndex; i < nums.length; i++) {
            path.add(nums[i]);
            backtrack(nums, i + 1, path, res);
            path.remove(path.size() - 1);
        }
    }
}
```

---

### 模板 2.2：元素有重不可复选（LeetCode 90. 子集 II · 树层去重）

- **题目**：给你一个整数数组 `nums`，其中可能包含重复元素，请你返回该数组所有可能的子集（不能包含重复的子集）。

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Solution90 {
    public List<List<Integer>> subsetsWithDup(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        Arrays.sort(nums); // 必须先排序
        backtrack(nums, 0, path, res);
        return res;
    }

    private void backtrack(int[] nums, int startIndex, List<Integer> path, List<List<Integer>> res) {
        // 每个节点都收集
        res.add(new ArrayList<>(path));

        for (int i = startIndex; i < nums.length; i++) {
            // 树层去重
            if (i > startIndex && nums[i] == nums[i - 1]) {
                continue;
            }

            path.add(nums[i]);
            backtrack(nums, i + 1, path, res);
            path.remove(path.size() - 1);
        }
    }
}
```

---

### 模板 2.3：不可排序的递增子序列（LeetCode 491. 非递减子序列 · 单层Set去重）

- **题目**：整数数组 `nums`，找出所有该数组中不同的递增子序列，递增子序列长度至少为 2。
- **致命陷阱**：这道题**绝对不能使用 `Arrays.sort()`**，因为一旦排序，就彻底改变了原数组元素之间的相对先后顺序！
- **解决方案**：在**单层递归的栈帧中创建一个局部 `HashSet`**（或数组哈希），只负责对当前层的同级分支去重。

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Solution491 {
    public List<List<Integer>> findSubsequences(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        backtrack(nums, 0, path, res);
        return res;
    }

    private void backtrack(int[] nums, int startIndex, List<Integer> path, List<List<Integer>> res) {
        // 收集长度 >= 2 的所有合法节点
        if (path.size() >= 2) {
            res.add(new ArrayList<>(path));
        }

        // 局部 HashSet：记录本层已经使用过的元素值，仅用于本层去重
        Set<Integer> usedInCurrentLevel = new HashSet<>();

        for (int i = startIndex; i < nums.length; i++) {
            // 递增检查：若小于 path 尾部元素，不合法
            if (!path.isEmpty() && nums[i] < path.get(path.size() - 1)) {
                continue;
            }
            // 树层去重：若本层同一父节点下已经选过该值，跳过
            if (usedInCurrentLevel.contains(nums[i])) {
                continue;
            }

            usedInCurrentLevel.add(nums[i]); // 记录该层已选
            path.add(nums[i]);
            backtrack(nums, i + 1, path, res);
            path.remove(path.size() - 1);
            // 注意：usedInCurrentLevel 不需要手动 remove，它是单层栈帧局部变量，天然随函数栈返回而销毁
        }
    }
}
```

---

## 五、题型三：排列问题 (Permutations)

> 💡 **排列 vs 组合的最关键区别**：  
> - 排列强调顺序，`[1, 2]` 和 `[2, 1]` 是两个不同的解。  
> - **循环没有 `startIndex`**，每次从 `0` 遍历到 `n - 1`。  
> - 依靠 **`boolean[] used`** 标记下标是否被选中。

### 模板 3.1：元素无重不可复选（LeetCode 46. 全排列）

```java
import java.util.ArrayList;
import java.util.List;

public class Solution46 {
    public List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        boolean[] used = new boolean[nums.length];
        backtrack(nums, used, path, res);
        return res;
    }

    private void backtrack(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> res) {
        // 终止条件：路径长度填满数组长度
        if (path.size() == nums.length) {
            res.add(new ArrayList<>(path));
            return;
        }

        // 排列问题：每次都从下标 0 开始遍历！
        for (int i = 0; i < nums.length; i++) {
            // 树枝去重：当前元素已经在当前路径中了，跳过
            if (used[i]) {
                continue;
            }

            used[i] = true;
            path.add(nums[i]);
            backtrack(nums, used, path, res);
            path.remove(path.size() - 1);
            used[i] = false; // 必须恢复 used 状态
        }
    }
}
```

---

### 模板 3.2：元素有重不可复选（LeetCode 47. 全排列 II · 树层去重）

- **核心重点**：必须排序！同时包含**树枝去重**与**树层去重**。
  - 树枝去重：`if (used[i]) continue;`
  - 树层去重：`if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) continue;`

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Solution47 {
    public List<List<Integer>> permuteUnique(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        boolean[] used = new boolean[nums.length];
        Arrays.sort(nums); // 1. 先排序
        backtrack(nums, used, path, res);
        return res;
    }

    private void backtrack(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> res) {
        if (path.size() == nums.length) {
            res.add(new ArrayList<>(path));
            return;
        }

        for (int i = 0; i < nums.length; i++) {
            // 树枝去重：当前下标已在当前递归树枝中
            if (used[i]) {
                continue;
            }

            // 树层去重：
            // !used[i - 1] 说明上一个相同元素刚刚回溯完（退出了上一个子树），
            // 此时正处于同一树层的下一个兄弟节点，必须跳过！
            if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) {
                continue;
            }

            used[i] = true;
            path.add(nums[i]);
            backtrack(nums, used, path, res);
            path.remove(path.size() - 1);
            used[i] = false;
        }
    }
}
```

---

## 六、题型四：分割问题 (Partitioning)

> 💡 **分割问题的本质**：  
> 很多同学以为分割是全新题型，其实**分割问题本质上就是组合问题**！  
> 切割线位置就是 `startIndex`，每次决策切割出一段区间 `[startIndex, i]`。若该段合法，则递归下一段从 `i + 1` 开始切。

### 模板 4.1：字符串分割为回文子串（LeetCode 131. 分割回文串）

```java
import java.util.ArrayList;
import java.util.List;

public class Solution131 {
    public List<List<String>> partition(String s) {
        List<List<String>> res = new ArrayList<>();
        List<String> path = new ArrayList<>();
        backtrack(s, 0, path, res);
        return res;
    }

    private void backtrack(String s, int startIndex, List<String> path, List<List<String>> res) {
        // 终止条件：切割线移动到了字符串最末尾，说明找到了一组全回文切割方案
        if (startIndex >= s.length()) {
            res.add(new ArrayList<>(path));
            return;
        }

        for (int i = startIndex; i < s.length(); i++) {
            // 判断当前截取的子串 [startIndex, i] 是否为回文
            if (isPalindrome(s, startIndex, i)) {
                path.add(s.substring(startIndex, i + 1));
                backtrack(s, i + 1, path, res); // 递归：从 i + 1 处作为下一个起点
                path.remove(path.size() - 1);   // 回溯
            }
        }
    }

    private boolean isPalindrome(String s, int left, int right) {
        while (left < right) {
            if (s.charAt(left++) != s.charAt(right--)) {
                return false;
            }
        }
        return true;
    }
}
```

---

### 模板 4.2：数字字符串恢复 IP 地址（LeetCode 93. 复原 IP 地址）

```java
import java.util.ArrayList;
import java.util.List;

public class Solution93 {
    public List<String> restoreIpAddresses(String s) {
        List<String> res = new ArrayList<>();
        // 长度剪枝：合法 IPv4 长度在 4 到 12 之间
        if (s.length() < 4 || s.length() > 12) {
            return res;
        }
        List<String> segments = new ArrayList<>();
        backtrack(s, 0, segments, res);
        return res;
    }

    private void backtrack(String s, int startIndex, List<String> segments, List<String> res) {
        // 已经切出了 4 段
        if (segments.size() == 4) {
            // 如果刚好耗尽了所有字符，则是有效解
            if (startIndex == s.length()) {
                res.add(String.join(".", segments));
            }
            return;
        }

        // 每段长度最多 3 个字符 (1 ~ 3)
        for (int len = 1; len <= 3 && startIndex + len <= s.length(); len++) {
            String segment = s.substring(startIndex, startIndex + len);
            if (isValidSegment(segment)) {
                segments.add(segment);
                backtrack(s, startIndex + len, segments, res);
                segments.remove(segments.size() - 1);
            }
        }
    }

    private boolean isValidSegment(String seg) {
        // 不能含前导 0（除单独 "0" 外）
        if (seg.length() > 1 && seg.charAt(0) == '0') {
            return false;
        }
        int val = Integer.parseInt(seg);
        return val >= 0 && val <= 255;
    }
}
```

---

## 七、题型五：棋盘与网格搜索 (Board & Grid)

### 模板 5.1：逐行决策棋盘（LeetCode 51. N 皇后 · void全量搜索）

- **特征**：每行必须且只能放一个皇后。因此以 **`row` 作为递归深度**，`col` 作为横向循环选择。

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Solution51 {
    public List<List<String>> solveNQueens(int n) {
        List<List<String>> res = new ArrayList<>();
        char[][] board = new char[n][n];
        for (char[] row : board) {
            Arrays.fill(row, '.');
        }
        backtrack(0, n, board, res);
        return res;
    }

    private void backtrack(int row, int n, char[][] board, List<List<String>> res) {
        // 终止条件：已成功安排完最后一行（0 到 n - 1 行都已妥当）
        if (row == n) {
            res.add(construct(board));
            return;
        }

        // 当前 row 行，尝试摆在每一列 col
        for (int col = 0; col < n; col++) {
            if (isValid(board, row, col, n)) {
                board[row][col] = 'Q';
                backtrack(row + 1, n, board, res); // 递归下一行
                board[row][col] = '.';             // 撤销
            }
        }
    }

    private boolean isValid(char[][] board, int row, int col, int n) {
        // 1. 检查同列上方是否有皇后
        for (int i = 0; i < row; i++) {
            if (board[i][col] == 'Q') return false;
        }
        // 2. 检查 135 度对角线（左上方）
        for (int i = row - 1, j = col - 1; i >= 0 && j >= 0; i--, j--) {
            if (board[i][j] == 'Q') return false;
        }
        // 3. 检查 45 度对角线（右上方）
        for (int i = row - 1, j = col + 1; i >= 0 && j < n; i--, j++) {
            if (board[i][j] == 'Q') return false;
        }
        return true;
    }

    private List<String> construct(char[][] board) {
        List<String> list = new ArrayList<>();
        for (char[] row : board) {
            list.add(new String(row));
        }
        return list;
    }
}
```

---

### 模板 5.2：二维空格填数字（LeetCode 37. 解数独 · boolean熔断搜索）

- **特征**：返回值必须是 **`boolean`**。因为数独只要找到任意一种解就直接交卷，继续递归只会把填好的棋盘覆盖掉。

```java
public class Solution37 {
    public void solveSudoku(char[][] board) {
        backtrack(board);
    }

    private boolean backtrack(char[][] board) {
        // 双重循环扫描棋盘上的每一个待填空格 '.'
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board[i][j] != '.') {
                    continue;
                }

                // 穷举填入 '1' 到 '9'
                for (char c = '1'; c <= '9'; c++) {
                    if (isValid(board, i, j, c)) {
                        board[i][j] = c;
                        // 核心：若下一层找到了最终解，立即逐层向上返回 true（熔断）
                        if (backtrack(board)) {
                            return true;
                        }
                        board[i][j] = '.'; // 回溯撤销
                    }
                }
                // 1 到 9 试了一圈都放不下，此路不通，返回 false
                return false;
            }
        }
        // 没有遇到任何 '.'，说明整个棋盘填满且合法！
        return true;
    }

    private boolean isValid(char[][] board, int row, int col, char c) {
        for (int k = 0; k < 9; k++) {
            // 同行冲突
            if (board[row][k] == c) return false;
            // 同列冲突
            if (board[k][col] == c) return false;
            // 3x3 九宫格冲突
            int boxRow = 3 * (row / 3) + k / 3;
            int boxCol = 3 * (col / 3) + k % 3;
            if (board[boxRow][boxCol] == c) return false;
        }
        return true;
    }
}
```

---

### 模板 5.3：二维网格四向搜索（LeetCode 79. 单词搜索 · 原地修改回溯）

- **特征**：二维矩阵中的路径搜索，使用原地修改字符（如将已访问格置为 `'#'`）来代替额外空间开销的 `visited[][]` 数组。

```java
public class Solution79 {
    public boolean exist(char[][] board, String word) {
        int m = board.length;
        int n = board[0].length;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                // 从所有与首字母匹配的格子起步搜索
                if (board[i][j] == word.charAt(0)) {
                    if (backtrack(board, word, i, j, 0)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean backtrack(char[][] board, String word, int r, int c, int index) {
        // 找到目标单词末尾，返回 true
        if (index == word.length()) {
            return true;
        }

        // 越界检查或字符不匹配
        if (r < 0 || r >= board.length || c < 0 || c >= board[0].length || board[r][c] != word.charAt(index)) {
            return false;
        }

        // 标记已访问（原地修改避免 new visited 数组）
        char temp = board[r][c];
        board[r][c] = '#';

        // 向上、下、左、右四个方向探索
        boolean found = backtrack(board, word, r + 1, c, index + 1)
                     || backtrack(board, word, r - 1, c, index + 1)
                     || backtrack(board, word, r, c + 1, index + 1)
                     || backtrack(board, word, r, c - 1, index + 1);

        // 恢复现场（回溯）
        board[r][c] = temp;

        return found;
    }
}
```

---

## 八、进阶：集合划分与桶视角 (Bucket Partition)

### 模板 6.1：划分为 K 个相等的子集（LeetCode 698 · 桶视角剪枝）

- **题目**：给定一个整数数组 `nums` 和一个正整数 `k`，找出是否有可能把这个数组分成 `k` 个非空子集，其总和都相等。
- **思维转换**：
  - **视角一（球选桶）**：每个数字看放在哪个桶里。复杂度为 $O(k^n)$，极易超时。
  - **视角二（桶选球）**：以每个桶为主角，一个桶装满了目标和，再装下一个桶。结合**从大到小降序排序**和**空桶去重**，效率极高！

```java
import java.util.Arrays;

public class Solution698 {
    public boolean canPartitionKSubsets(int[] nums, int k) {
        int sum = 0;
        for (int v : nums) sum += v;
        if (sum % k != 0) return false;
        int target = sum / k;

        // 升序排序
        Arrays.sort(nums);
        // 最大元素已超标
        if (nums[nums.length - 1] > target) return false;

        boolean[] used = new boolean[nums.length];
        // 从最后一个桶开始填，当前桶当前和为 0，选择起点从 nums.length - 1（倒序选大数）开始
        return backtrack(nums, k, 0, target, nums.length - 1, used);
    }

    private boolean backtrack(int[] nums, int k, int currentSum, int target, int startIndex, boolean[] used) {
        // 所有桶都成功装满
        if (k == 0) {
            return true;
        }

        // 当前桶已装满 target，开启下一个桶的填装，下一个桶重新从最大数字开始挑
        if (currentSum == target) {
            return backtrack(nums, k - 1, 0, target, nums.length - 1, used);
        }

        // 倒序遍历（优先选择大数，极大减少搜索分支）
        for (int i = startIndex; i >= 0; i--) {
            if (used[i] || currentSum + nums[i] > target) {
                continue;
            }

            used[i] = true;
            if (backtrack(nums, k, currentSum + nums[i], target, i - 1, used)) {
                return true;
            }
            used[i] = false;

            // 关键剪枝 1：如果当前是放入空桶的第一个数且失败了，后续相同的空桶必然都失败
            if (currentSum == 0) {
                return false;
            }
            // 关键剪枝 2：跳过相同数值的元素
            while (i > 0 && nums[i] == nums[i - 1]) {
                i--;
            }
        }
        return false;
    }
}
```

---

## 九、高频致命陷阱与编码规范

### 9.1 引用传递与“空集合”惨案
- ❌ **致命错误**：
  ```java
  res.add(path); // 错误！放入的是 path 对象的引用！
  ```
  因为后续 `path.remove(...)` 最终会把 `path` 清空，导致最后 `res` 里面装的全是一堆空的 `[]`！
- ✅ **正确写法**：
  ```java
  res.add(new ArrayList<>(path)); // 必须创建全新深拷贝副本
  ```

### 9.2 状态回退与对称性：做选择与撤销选择
- 回溯法永远保证：**进入下一层前加了什么，从下一层退出后必须成对删掉什么**。
  - `path.add(...)` 对应 `path.remove(path.size() - 1)`
  - `used[i] = true` 对应 `used[i] = false`
  - `board[r][c] = '#'` 对应 `board[r][c] = originalChar`
- 若对称性破坏，会导致后续搜索状态被污染，直接报 WA 或陷入无限死循环。

### 9.3 性能利器：`Deque` vs `ArrayList` vs `int[]`
- 在 Java 中，`path` 既可以用 `List<Integer> path = new ArrayList<>()`，也可以用 `Deque<Integer> path = new ArrayDeque<>()`。
  - `ArrayList` 的 `add()` 和 `remove(size - 1)` 在末尾操作都是 $O(1)$，开销极小。
  - 如果频繁在字符串上回溯，切忌直接使用 `s + "..."` 造成大量的字符串重新分配与垃圾回收，优先使用 **`StringBuilder`（`sb.append` 与 `sb.deleteCharAt`）** 或 **`char[]` 数组原地替换**。

---

## 十、回溯刷题路线推荐（先简后难闭环）

按照以下顺序集中突破，每做完一类题目回顾对应模板，回溯法即可彻底通关：

1. **第一关（基础组合与子集 · 熟悉 `startIndex`）**：
   - LeetCode 77. 组合
   - LeetCode 78. 子集
   - LeetCode 216. 组合总和 III
2. **第二关（去重风暴 · 掌握树层去重）**：
   - LeetCode 90. 子集 II
   - LeetCode 40. 组合总和 II
   - LeetCode 491. 非递减子序列（单层 HashSet 去重）
3. **第三关（元素复选 · 灵活控制起点）**：
   - LeetCode 39. 组合总和（传 `i` 而非 `i + 1`）
4. **第四关（排列大家族 · 掌握 `used[]`）**：
   - LeetCode 46. 全排列
   - LeetCode 47. 全排列 II（排序 + `!used[i-1]` 树层去重）
5. **第五关（分割问题 · 化归组合）**：
   - LeetCode 131. 分割回文串
   - LeetCode 93. 复原 IP 地址
6. **第六关（棋盘与矩阵 · 掌握 `boolean` 熔断）**：
   - LeetCode 51. N 皇后
   - LeetCode 37. 解数独
   - LeetCode 79. 单词搜索
7. **第七关（进阶分桶 · 终极剪枝）**：
   - LeetCode 698. 划分为k个相等的子集
   - LeetCode 473. 火柴拼正方形

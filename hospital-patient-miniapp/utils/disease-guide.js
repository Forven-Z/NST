/** 按疾病/症状 → 推荐科室 */
module.exports = [
  { id: 1, name: '发热、咳嗽', deptId: 1, deptName: '内科', hint: '常见上呼吸道感染、感冒等' },
  { id: 2, name: '头痛、头晕', deptId: 1, deptName: '内科', hint: '可先挂普通号初诊' },
  { id: 3, name: '腹痛、腹泻', deptId: 1, deptName: '内科', hint: '消化相关问题' },
  { id: 4, name: '外伤、肿块', deptId: 6, deptName: '外科', hint: '需外科评估时选择' },
  { id: 5, name: '儿童发热', deptId: 8, deptName: '儿科', hint: '14 周岁以下建议儿科' },
  { id: 6, name: '产检、妇科', deptId: 9, deptName: '妇产科', hint: '孕产及妇科问题' },
  { id: 7, name: '不确定', deptId: 1, deptName: '内科', hint: '可先挂内科普通号分诊' },
]

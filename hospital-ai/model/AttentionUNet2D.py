import torch
import torch.nn as nn

# 时空注意力
class SpatialTemporalAttention(nn.Module):
	def __init__(self, dim, num_heads=4, qkv_bias=False):
		super().__init__()
		self.num_heads = num_heads  # 注意力头数，默认4头
		head_dim = dim // num_heads  # 每个头的维度 = 总维度//头数
		self.scale = head_dim ** -0.5  # 缩放因子 1/√d_k 防止数值爆炸
		# 用一个线性层，同时生成 Q、K、V 三个矩阵（输出维度 3*dim）
		self.qkv = nn.Linear(dim, dim * 3, bias=qkv_bias)
		# 输出投影层
		self.proj = nn.Linear(dim, dim)

	def forward(self, x):
		# 输入 x 形状：[B, C, H, W] → 典型如 [B, 512, 8, 8]
		B, C, H, W = x.shape
		# 1. 展平空间维度 + 调整维度顺序
		# [B, C, H, W] → [B, C, H*W] → [B, H*W, C]
		x = x.flatten(2).transpose(1, 2)
		# 2. 经过线性层得到 QKV → 分成3份
		# [B, N, C] → [B, N, 3C]
		qkv = self.qkv(x)
		# 3. 重塑 + 变换维度，拆成 Q、K、V
		# reshape → [B, N, 3, heads, head_dim]
		# permute → [3, B, heads, N, head_dim]
		qkv = qkv.reshape(B, -1, 3, self.num_heads, C // self.num_heads).permute(2, 0, 3, 1, 4)
		q, k, v = qkv[0], qkv[1], qkv[2]  # 每个形状：[B, heads, N, head_dim]
		# 4. 计算注意力分数：Q × K^T / √d_k
		attn = (q @ k.transpose(-2, -1)) * self.scale
		# 5. Softmax 归一化成概率
		attn = attn.softmax(dim=-1)
		# 6. 注意力分数 × V
		x = (attn @ v).transpose(1, 2).reshape(B, -1, C)
		# 7. 最终投影
		x = self.proj(x)
		# 8. 形状还原回 [B, C, H, W]
		x = x.transpose(1, 2).reshape(B, C, H, W)
		return x

# 双卷积
class DoubleConv(nn.Module):
	def __init__(self, in_c, out_c):
		super().__init__()
		self.conv = nn.Sequential(
			nn.Conv2d(in_c, out_c, 3, padding=1),
			nn.BatchNorm2d(out_c),
			nn.ReLU(inplace=True),
			nn.Conv2d(out_c, out_c, 3, padding=1),
			nn.BatchNorm2d(out_c),
			nn.ReLU(inplace=True)
		)

	def forward(self, x):
		return self.conv(x)

# UNet 主模型
class UNet2D(nn.Module):
	def __init__(self, in_ch=1, out_ch=1):
		super().__init__()
		print("\n[Model] Attention UNet2D")
		self.pool = nn.MaxPool2d(2)
		self.d1 = DoubleConv(in_ch, 64)
		self.d2 = DoubleConv(64, 128)
		self.d3 = DoubleConv(128, 256)
		self.d4 = DoubleConv(256, 512)
		self.spatial_temporal_attn = SpatialTemporalAttention(dim=512, num_heads=4)
		self.up4 = nn.ConvTranspose2d(512, 256, 2, stride=2)
		self.up3 = nn.ConvTranspose2d(256, 128, 2, stride=2)
		self.up2 = nn.ConvTranspose2d(128, 64, 2, stride=2)
		self.u4 = DoubleConv(512, 256)
		self.u3 = DoubleConv(256, 128)
		self.u2 = DoubleConv(128, 64)
		self.out = nn.Conv2d(64, out_ch, kernel_size=1)
		# 推理前向时自动写入，无额外可训练参数
		self.fused_feature = None

	def forward(self, x):
		d1 = self.d1(x)
		d2 = self.d2(self.pool(d1))
		d3 = self.d3(self.pool(d2))
		d4 = self.d4(self.pool(d3))
		d4 = self.spatial_temporal_attn(d4)

		# 多层全局平均池化融合特征（64+128+256+512=960 维）
		feat = torch.cat(
			[
				torch.mean(d1, dim=(2, 3)),
				torch.mean(d2, dim=(2, 3)),
				torch.mean(d3, dim=(2, 3)),
				torch.mean(d4, dim=(2, 3)),
			],
			dim=1,
		)
		self.fused_feature = torch.nn.functional.normalize(feat, p=2, dim=1)

		u4 = self.u4(torch.cat([self.up4(d4), d3], dim=1))
		u3 = self.u3(torch.cat([self.up3(u4), d2], dim=1))
		u2 = self.u2(torch.cat([self.up2(u3), d1], dim=1))
		out = self.out(u2)
		return out

	def extract_features(self):
		"""返回最近一次 forward 记录的融合特征向量 [B, 960]。"""
		if self.fused_feature is None:
			raise ValueError("请先执行一次 model(x) 前向，再调用 extract_features()。")
		return self.fused_feature
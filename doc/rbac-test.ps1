$ErrorActionPreference='Stop'
$base='http://localhost:8080/api'
function Login($u,$p){ (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{username=$u;password=$p}|ConvertTo-Json)).data }
function Call($method,$path,$token,$body){
  $h=@{}; if($token){$h['Authorization']="Bearer $token"}
  try {
    $r=Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $h -ContentType 'application/json' -Body ($body|ConvertTo-Json -Depth 5)
    return "OK $($r.code) $($r.message)"
  } catch {
    $resp=$_.Exception.Response
    if($resp){ $sr=New-Object IO.StreamReader($resp.GetResponseStream()); $txt=$sr.ReadToEnd(); return "HTTP $([int]$resp.StatusCode) $txt" }
    return "ERR $($_.Exception.Message)"
  }
}
# 1. 注册新租户 rbac_a（普通管理员）
$reg=(Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType 'application/json' -Body (@{username='rbac_a';password='rbac123456';displayName='RBAC Admin';companyName='RBAC Test Corp'}|ConvertTo-Json)).data
$tokA=$reg.token; $tenantId=$reg.tenantId
Write-Host "1. 注册租户管理员 rbac_a tenantId=$tenantId role=$($reg.role)"
# 2. 系统管理员登录
$s=Login 'admin' 'Admin@123456'; $tokS=$s.token
Write-Host "2. 系统管理员 admin 登录 OK role=$($s.role) tenantId=$($s.tenantId)"
# 3. 系统管理员看所有租户用户
$users=(Invoke-RestMethod -Method Get -Uri "$base/users" -Headers @{Authorization="Bearer $tokS"}).data
$hit=$users|Where-Object{$_.username -eq 'rbac_a'}|ForEach-Object{"$($_.username)/$($_.role)/tenant=$($_.tenantName)"}
Write-Host "3. 系统管理员看用户数: $($users.Count)  含 rbac_a: $hit"
# 4. 系统管理员创建系统管理员
Write-Host "4. S创建系统管理员 sys2 => $(Call 'Post' '/users' $tokS @{username='sys2';password='sys123456';displayName='Sys2';role='admin'})"
# 5. 系统管理员创建平台普通用户
Write-Host "5. S创建平台普通用户 plat_user => $(Call 'Post' '/users' $tokS @{username='plat_user';password='plat123456';displayName='Plat';role='operator'})"
# 6. 租户管理员看本租户
$usersA=(Invoke-RestMethod -Method Get -Uri "$base/users" -Headers @{Authorization="Bearer $tokA"}).data
Write-Host "6. 租户管理员看用户数: $($usersA.Count)  用户名: $($usersA.username -join ',')"
# 7. 租户管理员创建普通用户
Write-Host "7. A创建本租户普通用户 member1 => $(Call 'Post' '/users' $tokA @{username='member1';password='member1234';displayName='Member1';role='operator'})"
# 8. 租户管理员尝试创建管理员（提权防护）
Write-Host "8. A尝试创建管理员 evil_admin => $(Call 'Post' '/users' $tokA @{username='evil_admin';password='evil123456';role='admin'})"
# 9. 租户管理员禁用本租户普通用户
$member1=(Invoke-RestMethod -Method Get -Uri "$base/users" -Headers @{Authorization="Bearer $tokA"}).data|Where-Object{$_.username -eq 'member1'}
Write-Host "9. A禁用 member1(id=$($member1.id)) => $(Call 'Put' "/users/$($member1.id)/status" $tokA @{status='disabled'})"
# 10. 租户管理员操作跨租户用户（系统管理员 sys2）
$sys2=(Invoke-RestMethod -Method Get -Uri "$base/users" -Headers @{Authorization="Bearer $tokS"}).data|Where-Object{$_.username -eq 'sys2'}
Write-Host "10. A禁跨租户 sys2(id=$($sys2.id)) => $(Call 'Put' "/users/$($sys2.id)/status" $tokA @{status='disabled'})"
# 11. 租户管理员禁自己（管理员）
$rbac=(Invoke-RestMethod -Method Get -Uri "$base/users" -Headers @{Authorization="Bearer $tokA"}).data|Where-Object{$_.username -eq 'rbac_a'}
Write-Host "11. A禁自己(管理员) => $(Call 'Put' "/users/$($rbac.id)/status" $tokA @{status='disabled'})"
# 12. 普通用户访问用户管理
$m=Login 'member1' 'member1234'
Write-Host "12. 普通用户 member1 GET /users => $(Call 'Get' '/users' $m.token)"
# 13. 系统管理员禁用/恢复租户管理员
Write-Host "13. S禁用租户管理员 rbac_a => $(Call 'Put' "/users/$($rbac.id)/status" $tokS @{status='disabled'})"
Write-Host "13b. S恢复租户管理员 rbac_a => $(Call 'Put' "/users/$($rbac.id)/status" $tokS @{status='active'})"
# 14. 系统管理员不能禁用其他系统管理员
Write-Host "14. S禁其他系统管理员 sys2 => $(Call 'Put' "/users/$($sys2.id)/status" $tokS @{status='disabled'})"
